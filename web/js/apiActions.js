// Copyright © 2015-2017 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import type {Region, Territory} from "./api";

export type TerritoriesLoadedAction = { type: 'TERRITORIES_LOADED', territories: Array<Territory> }
export const TERRITORIES_LOADED = 'TERRITORIES_LOADED';
export const territoriesLoaded = (territories: Array<Territory>): TerritoriesLoadedAction => ({
  type: TERRITORIES_LOADED,
  territories
});

export type RegionsLoadedAction = { type: 'REGIONS_LOADED', regions: Array<Region> }
export const REGIONS_LOADED = 'REGIONS_LOADED';
export const regionsLoaded = (regions: Array<Region>): RegionsLoadedAction => ({
  type: REGIONS_LOADED,
  regions
});

export type ApiAction =
  | TerritoriesLoadedAction
  | RegionsLoadedAction;
