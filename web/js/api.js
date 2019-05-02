// Copyright © 2015-2019 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import {api} from "./util";
import alphanumSort from "alphanum-sort";
import sortBy from "lodash/sortBy";
import findIndex from "lodash/findIndex";
import {unstable_createResource} from "@luontola/react-cache";

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
  SettingsCache = unstable_createResource(async () => {
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
}

let CongregationsCache;

function refreshCongregations() {
  CongregationsCache = unstable_createResource(async () => {
      const response = await api.get('/api/congregations');
      return response.data;
    }
  );
}

refreshCongregations();

export function getCongregations(): Array<Congregation> {
  return CongregationsCache.read();
}

export async function createCongregation(name: string) {
  const response = await api.post('/api/congregations', {name});
  refreshCongregations();
  return response.data.id;
}


// ====== Territories ======

export type Territory = {
  id: number,
  number: string,
  address: string,
  region: string,
  location: string,
}

// TODO: create library definitions for alphanum-sort and lodash-es, so that we can remove all this type noise
// https://flow.org/en/docs/libdefs/creation/

function sortTerritories(territories: Array<Territory>): Array<Territory> {
  const numbers: Array<string> = alphanumSort(territories.map((t: Territory) => t.number));
  return sortBy(territories, (t: Territory) => findIndex(numbers, (n: string) => n === t.number))
}

export async function getTerritories(congregationId: ?string): Promise<Array<Territory>> {
  const response = await api.get('/api/territories', requestConfig(congregationId));
  return sortTerritories(response.data);
}


// ====== Regions ======

export type Region = {
  id: number,
  name: string,
  minimap_viewport: boolean,
  congregation: boolean,
  subregion: boolean,
  location: string,
}

function sortRegions(regions: Array<Region>): Array<Region> {
  return sortBy(regions, (r: Region) => r.name.toLowerCase());
}

export async function getRegions(congregationId: ?string): Promise<Array<Region>> {
  const response = await api.get('/api/regions', requestConfig(congregationId));
  return sortRegions(response.data);
}
