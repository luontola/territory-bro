// Copyright © 2015-2019 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import {api} from "./util";
import alphanumSort from "alphanum-sort";
import sortBy from "lodash/sortBy";
import keyBy from "lodash/keyBy";
import findIndex from "lodash/findIndex";
import {unstable_createResource} from "@luontola/react-cache";
import WKT from "ol/format/WKT";
import MultiPolygon from "ol/geom/MultiPolygon";

function requestConfig(congregationId: ?string) {
  const config = {
    headers: {}
  };
  if (congregationId) {
    config.headers['X-Tenant'] = congregationId;
  }
  return config;
}


// ====== Settings & Authentication ======

export type Settings = {
  dev: boolean,
  auth0: {
    domain: string,
    clientId: string,
  },
  supportEmail: string,
  user: {
    authenticated: boolean,
    name: ?string,
    sub: ?string,
  },
}

let SettingsCache;

export function refreshSettings() {
  console.info("Reset settings cache");
  SettingsCache = unstable_createResource(async () => {
      console.info("Fetch settings");
      const response = await api.get('/api/settings');
      return response.data
    }
  );
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


// ====== Congregations ======

export type Congregation = {
  id: string,
  name: string,
  location: string,
  congregationBoundaries: Array<Boundary>,
  territories: Array<Territory>,
  getTerritoryById: (string) => Territory,
  subregions: Array<Subregion>,
  getSubregionById: (string) => Subregion,
  cardMinimapViewports: Array<Viewport>
}

let CongregationsCache;
let CongregationsByIdCache;

function refreshCongregations() {
  console.info("Reset congregations cache");
  CongregationsCache = unstable_createResource(async () => {
      console.info("Fetch congregations");
      const response = await api.get('/api/congregations');
      return response.data;
    }
  );
  CongregationsByIdCache = unstable_createResource(async (congregationId) => {
      console.info(`Fetch congregation ${congregationId}`);
      const response = await api.get(`/api/congregation/${congregationId}`);
      const congregation: Congregation = response.data;
      congregation.territories = sortTerritories(congregation.territories);
      congregation.subregions = sortSubregions(congregation.subregions);
      congregation.territories.forEach(territory => {
        territory.enclosingSubregion = getEnclosing(territory.location,
          congregation.subregions.map(subregion => subregion.location));
        territory.enclosingMinimapViewport = getEnclosing(territory.location,
          congregation.cardMinimapViewports.map(viewport => viewport.location));
      });
      congregation.location = mergeMultiPolygons(
        congregation.congregationBoundaries.map(boundary => boundary.location)) ||
        "MULTIPOLYGON(((180 90,180 -90,-180 -90,-180 90,180 90)))";

      const territoriesById = keyBy(congregation.territories, 'id');
      congregation.getTerritoryById = (id) => territoriesById[id] || (() => {
        throw Error(`Territory not found: ${id}`)
      })();

      const subregionsById = keyBy(congregation.subregions, 'id');
      congregation.getSubregionById = (id) => subregionsById[id] || (() => {
        throw Error(`Subregion not found: ${id}`)
      })();

      return congregation;
    }
  )
}

function getEnclosing(innerWkt: string, enclosingCandidateWkts: Array<string>): ?string {
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

function mergeMultiPolygons(multiPolygons: Array<string>): ?string {
  if (multiPolygons.length === 0) {
    return null;
  }
  const wkt = new WKT();
  const merged = multiPolygons
    .map(p => wkt.readFeature(p).getGeometry())
    .reduce((a, b) => new MultiPolygon([...a.getPolygons(), ...b.getPolygons()]));
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


// ====== Territories ======

export type Territory = {
  id: string,
  number: string,
  addresses: string,
  subregion: string,
  location: string,
  enclosingSubregion: ?string,
  enclosingMinimapViewport: ?string,
}

// TODO: create library definitions for alphanum-sort and lodash-es, so that we can remove all this type noise
// https://flow.org/en/docs/libdefs/creation/

function sortTerritories(territories: Array<Territory>): Array<Territory> {
  const numbers: Array<string> = alphanumSort(territories.map((t: Territory) => t.number));
  return sortBy(territories, (t: Territory) => findIndex(numbers, (n: string) => n === t.number))
}

// ====== Regions ======

export type Subregion = {
  id: string,
  name: string,
  location: string,
}

function sortSubregions(subregions: Array<Subregion>): Array<Subregion> {
  return sortBy(subregions, (r: Subregion) => r.name.toLowerCase());
}

export type Boundary = {
  id: string,
  location: string,
}

export type Viewport = {
  id: string,
  location: string,
}
