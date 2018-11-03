// Copyright © 2015-2018 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import type {MapRaster} from "./maps/mapOptions";
import type {ConfigAction} from "./configActions";
import {CONGREGATION_CHANGED, MAP_RASTERS_LOADED} from "./configActions";

export type ConfigState = {
  +congregationId: ?string,
  +mapRasters: Array<MapRaster>,
};

const defaultState: ConfigState = {
  congregationId: null,
  mapRasters: [],
};

export default (state: ConfigState = defaultState, action: ConfigAction): ConfigState => {
  switch (action.type) {
    case CONGREGATION_CHANGED:
      return {
        ...state,
        congregationId: action.congregationId,
      };
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
