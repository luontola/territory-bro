// Copyright © 2015-2017 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import type {MapRaster} from "./maps/mapOptions";
import type {ConfigAction} from "./configActions";
import {MAP_RASTERS_LOADED} from "./configActions";

export type ConfigState = {
  +mapRasters: Array<MapRaster>,
};

const defaultState: ConfigState = {
  mapRasters: [],
};

export default (state: ConfigState = defaultState, action: ConfigAction): ConfigState => {
  switch (action.type) {
    case MAP_RASTERS_LOADED:
      return {
        ...state,
        mapRasters: action.mapRasters,
      };
    default:
      (action: empty); // type check that all action types are handled
      return state;
  }
};
