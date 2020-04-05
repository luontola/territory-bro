// Copyright © 2015-2020 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import {api} from "./util";
import alphanumSort from "alphanum-sort";
import sortBy from "lodash/sortBy";
import keyBy from "lodash/keyBy";
import findIndex from "lodash/findIndex";
import {unstable_createResource} from "@luontola/react-cache";
import WKT from "ol/format/WKT";
import MultiPolygon from "ol/geom/MultiPolygon";

function requestConfig(congregationId: string | null | undefined) {
  const config = {
    headers: {}
  };
  if (congregationId) {
    config.headers['X-Tenant'] = congregationId;
  }
  return config;
}


// ====== Settings & Authentication ======

export type User = {
  id: string;
  sub: string | null | undefined;
  name: string | null | undefined;
  nickname: string | null | undefined;
  email: string | null | undefined;
  emailVerified: boolean | null | undefined;
  picture: string | null | undefined;
};

export type Settings = {
  dev: boolean;
  auth0: {
    domain: string;
    clientId: string;
  };
  supportEmail: string;
  demoAvailable: boolean;
  user: User | null | undefined;
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
  return sortBy(users, [(u: User) => (u.name || '').toLowerCase(), 'id']);
}


// ====== Congregations ======

export type Congregation = {
  id: string;
  name: string;
  permissions: {
    [permission: string]: boolean;
  };
  location: string;
  congregationBoundaries: Array<Boundary>;
  territories: Array<Territory>;
  getTerritoryById: (arg0: string) => Territory;
  subregions: Array<Subregion>;
  getSubregionById: (arg0: string) => Subregion;
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
  return sortBy(congregations, (c: Congregation) => c.name.toLowerCase());
}

export function enrichCongregation(congregation: Congregation): Congregation {
  congregation.users = sortUsers(congregation.users);
  congregation.territories = sortTerritories(congregation.territories);
  congregation.subregions = sortSubregions(congregation.subregions);
  congregation.territories.forEach(territory => {
    territory.enclosingSubregion = getEnclosing(territory.location, congregation.subregions.map(subregion => subregion.location));
    territory.enclosingMinimapViewport = getEnclosing(territory.location, congregation.cardMinimapViewports.map(viewport => viewport.location));
  });
  congregation.location = mergeMultiPolygons(congregation.congregationBoundaries.map(boundary => boundary.location)) || "MULTIPOLYGON(((180 90,180 -90,-180 -90,-180 90,180 90)))";

  const territoriesById = keyBy(congregation.territories, 'id');
  congregation.getTerritoryById = id => territoriesById[id] || (() => {
    throw Error(`Territory not found: ${id}`);
  })();

  const subregionsById = keyBy(congregation.subregions, 'id');
  congregation.getSubregionById = id => subregionsById[id] || (() => {
    throw Error(`Subregion not found: ${id}`);
  })();

  return congregation;
}

function getEnclosing(innerWkt: string, enclosingCandidateWkts: Array<string>): string | null | undefined {
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

function mergeMultiPolygons(multiPolygons: Array<string>): string | null | undefined {
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

export async function renameCongregation(congregationId: string, name: string) {
  await api.post(`/api/congregation/${congregationId}/rename`, {name});
  refreshCongregations();
}


// ====== Territories ======

export type Territory = {
  id: string;
  number: string;
  addresses: string;
  subregion: string;
  location: string;
  enclosingSubregion: string | null | undefined;
  enclosingMinimapViewport: string | null | undefined;
};

// TODO: create library definitions for alphanum-sort and lodash-es, so that we can remove all this type noise
// https://flow.org/en/docs/libdefs/creation/

function sortTerritories(territories: Array<Territory>): Array<Territory> {
  const numbers: Array<string> = alphanumSort(territories.map((t: Territory) => t.number));
  return sortBy(territories, (t: Territory) => findIndex(numbers, (n: string) => n === t.number));
}

// ====== Regions ======

export type Subregion = {
  id: string;
  name: string;
  location: string;
};

function sortSubregions(subregions: Array<Subregion>): Array<Subregion> {
  return sortBy(subregions, (r: Subregion) => r.name.toLowerCase());
}

export type Boundary = {
  id: string;
  location: string;
};

export type Viewport = {
  id: string;
  location: string;
};
