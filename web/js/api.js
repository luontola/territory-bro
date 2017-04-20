// Copyright © 2015-2017 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import {api} from "./util";
import alphanumSort from "alphanum-sort";
import sortBy from "lodash-es/sortBy";
import findIndex from "lodash-es/findIndex";

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

export async function getTerritories(): Promise<Array<Territory>> {
  const response = await api.get('/api/territories');
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

export async function getRegions(): Promise<Array<Region>> {
  const response = await api.get('/api/regions');
  return sortRegions(response.data);
}
