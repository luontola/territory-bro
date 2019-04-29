// Copyright © 2015-2019 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import {api} from "./util";
import alphanumSort from "alphanum-sort";
import sortBy from "lodash/sortBy";
import findIndex from "lodash/findIndex";

function requestConfig(congregationId: ?string) {
  const config = {
    headers: {}
  };
  if (congregationId) {
    config.headers['X-Tenant'] = congregationId;
  }
  return config;
}


export type Congregation = {
  id: string,
  name: string,
}

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
  congregations: Array<Congregation>,
}

export async function getSettings(): Promise<Settings> {
  const response = await api.get('/api/settings');
  return response.data
}


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

export async function loginWithIdToken(idToken: string) {
  await api.post('/api/login', {idToken});
}

export async function devLogin() {
  await api.post('/api/dev-login', {sub: "developer", name: "Developer", email: "developer@example.com"});
}

export async function logout() {
  await api.post('/api/logout');
}

export async function createCongregation(name: string) {
  const response = await api.post('/api/congregations', {name});
  return response.data.id;
}
