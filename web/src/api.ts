// Copyright © 2015-2023 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import {api} from "./util";
import alphanumSort from "alphanum-sort";
import {findIndex, keyBy, sortBy} from "lodash-es";
import {unstable_createResource} from "@luontola/react-cache";
import WKT from "ol/format/WKT";
import MultiPolygon from "ol/geom/MultiPolygon";

// ====== Settings & Authentication ======

export type User = {
  id: string;
  sub?: string;
  name?: string;
  nickname?: string;
  email?: string;
  emailVerified?: boolean;
  picture?: string;
};

export type Settings = {
  dev: boolean;
  auth0: {
    domain: string;
    clientId: string;
  };
  supportEmail: string;
  demoAvailable: boolean;
  user?: User;
};

let SettingsCache;

export function refreshSettings() {
  console.info("Reset settings cache");
  SettingsCache = unstable_createResource(async () => {
    console.info("Fetch settings");
    const response = await api.get('/api/settings');
    return response.data;
  });
}

refreshSettings();

export function getSettings(): Settings {
  return SettingsCache.read();
}

export async function loginWithIdToken(idToken: string) {
  await api.post('/api/login', {idToken});
  refreshSettings();
}

export async function devLogin() {
  await api.post('/api/dev-login', {sub: "developer", name: "Developer", email: "developer@example.com"});
  refreshSettings();
}

export async function logout() {
  await api.post('/api/logout');
  refreshSettings();
}

function sortUsers(users: Array<User>): Array<User> {
  return sortBy(users, [u => (u.name || '').toLowerCase(), 'id']);
}


// ====== Congregations ======

export type Congregation = {
  id: string;
  name: string;
  permissions: {
    [permission: string]: boolean;
  };
  loansCsvUrl?: string;
  location: string;
  congregationBoundaries: Array<Boundary>;
  territories: Array<Territory>;
  getTerritoryById: (id: string) => Territory;
  regions: Array<Region>;
  getRegionById: (id: string) => Region;
  cardMinimapViewports: Array<Viewport>;
  users: Array<User>;
};

let CongregationsCache;
let CongregationsByIdCache;

function refreshCongregations() {
  console.info("Reset congregations cache");
  CongregationsCache = unstable_createResource(async () => {
    console.info("Fetch congregations");
    const response = await api.get('/api/congregations');
    return sortCongregations(response.data);
  });
  CongregationsByIdCache = unstable_createResource(async congregationId => {
    console.info(`Fetch congregation ${congregationId}`);
    const response = await api.get(`/api/congregation/${congregationId}`);
    return enrichCongregation(response.data);
  });
}

function sortCongregations(congregations: Array<Congregation>): Array<Congregation> {
  return sortBy(congregations, c => c.name.toLowerCase());
}

export function enrichCongregation(congregation): Congregation {
  congregation.users = sortUsers(congregation.users);
  congregation.territories = sortTerritories(congregation.territories);
  congregation.regions = sortRegions(congregation.regions);
  congregation.territories.forEach(territory => {
    territory.enclosingRegion = getEnclosing(territory.location, congregation.regions.map(region => region.location));
    territory.enclosingMinimapViewport = getEnclosing(territory.location, congregation.cardMinimapViewports.map(viewport => viewport.location));
  });
  congregation.location = mergeMultiPolygons(congregation.congregationBoundaries.map(boundary => boundary.location)) || "MULTIPOLYGON(((180 90,180 -90,-180 -90,-180 90,180 90)))";

  const territoriesById = keyBy(congregation.territories, 'id');
  congregation.getTerritoryById = id => territoriesById[id] || (() => {
    throw Error(`Territory not found: ${id}`);
  })();

  const regionsById = keyBy(congregation.regions, 'id');
  congregation.getRegionById = id => regionsById[id] || (() => {
    throw Error(`Region not found: ${id}`);
  })();

  return congregation;
}

function getEnclosing(innerWkt: string, enclosingCandidateWkts: Array<string>): string | null {
  const wkt = new WKT();
  // TODO: sort by overlapping area instead of center point
  const centerPoint = wkt.readFeature(innerWkt).getGeometry().getInteriorPoints().getPoint(0);
  const territoryCoordinate = centerPoint.getCoordinates();

  let result = null;
  enclosingCandidateWkts.forEach(enclosing => {
    if (wkt.readFeature(enclosing).getGeometry().intersectsCoordinate(territoryCoordinate)) {
      result = enclosing;
    }
  });
  return result;
}

function mergeMultiPolygons(multiPolygons: Array<string>): string | null {
  if (multiPolygons.length === 0) {
    return null;
  }
  const wkt = new WKT();
  const merged = multiPolygons.map(p => wkt.readFeature(p).getGeometry()).reduce((a, b) => new MultiPolygon([...a.getPolygons(), ...b.getPolygons()]));
  return wkt.writeGeometry(merged);
}

refreshCongregations();

export function getCongregations(): Array<Congregation> {
  return CongregationsCache.read();
}

export function getCongregationById(congregationId: string): Congregation {
  return CongregationsByIdCache.read(congregationId);
}

export async function createCongregation(name: string) {
  const response = await api.post('/api/congregations', {name});
  refreshCongregations();
  return response.data.id;
}

export async function addUser(congregationId: string, userId: string) {
  await api.post(`/api/congregation/${congregationId}/add-user`, {userId});
  refreshCongregations();
}

export async function setUserPermissions(congregationId: string, userId: string, permissions: Array<string>) {
  await api.post(`/api/congregation/${congregationId}/set-user-permissions`, {userId, permissions});
  refreshCongregations();
}

export async function saveCongregationSettings(congregationId: string, congregationName: string, loansCsvUrl) {
  await api.post(`/api/congregation/${congregationId}/settings`, {congregationName, loansCsvUrl});
  refreshCongregations();
}


// ====== Territories ======

export type Territory = {
  id: string;
  number: string;
  addresses: string;
  region: string;
  location: string;
  loaned?: boolean;
  staleness?: number;
  meta: { [key: string]: any };
  enclosingRegion?: string;
  enclosingMinimapViewport?: string;
};

function sortTerritories(territories: Array<Territory>): Array<Territory> {
  const numbers: Array<string> = alphanumSort(territories.map(t => t.number));
  return sortBy(territories, t => findIndex(numbers, n => n === t.number));
}

export async function shareTerritory(congregationId: string, territoryId: string) {
  const response = await api.post(`/api/congregation/${congregationId}/territory/${territoryId}/share`, {});
  return response.data.url;
}

export async function openShare(shareKey: string) {
  const response = await api.get(`/api/share/${shareKey}`);
  return response.data;
}

export async function generateQrCodes(congregationId: string, territoryIds: Array<string>) {
  const response = await api.post(`/api/congregation/${congregationId}/generate-qr-codes`, {territories: territoryIds})
  return response.data.qrCodes;
}

// ====== Regions ======

export type Region = {
  id: string;
  name: string;
  location: string;
};

function sortRegions(regions: Array<Region>): Array<Region> {
  return sortBy(regions, (r: Region) => r.name.toLowerCase());
}

export type Boundary = {
  id: string;
  location: string;
};

export type Viewport = {
  id: string;
  location: string;
};
