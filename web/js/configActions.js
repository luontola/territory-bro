// Copyright © 2015-2017 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import type {MapRaster} from "./maps/mapOptions";

export type MapRastersLoadedAction = { type: 'MAP_RASTERS_LOADED', mapRasters: Array<MapRaster> }
export const MAP_RASTERS_LOADED = 'MAP_RASTERS_LOADED';
export const mapRastersLoaded = (mapRasters: Array<MapRaster>): MapRastersLoadedAction => ({
  type: MAP_RASTERS_LOADED,
  mapRasters
});

export type ConfigAction =
  | MapRastersLoadedAction;
