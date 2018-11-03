// Copyright © 2015-2018 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import type {ApiAction} from "./apiActions";
import {MY_CONGREGATIONS_LOADED, REGIONS_LOADED, TERRITORIES_LOADED, USER_LOGGED_IN} from "./apiActions";
import type {Congregation, Region, Territory} from "./api";

export type ApiState = {
  +authenticated: boolean,
  +userFullName: ?string,
  +congregations: Array<Congregation>,
  +territories: Array<Territory>,
  +regions: Array<Region>,
};

const defaultState: ApiState = {
  authenticated: false,
  userFullName: null,
  congregations: [],
  territories: [],
  regions: [],
};

export default (state: ApiState = defaultState, action: ApiAction): ApiState => {
  switch (action.type) {
    case USER_LOGGED_IN:
      return {
        ...state,
        authenticated: true,
        userFullName: action.fullName
      };
    case MY_CONGREGATIONS_LOADED:
      return {
        ...state,
        congregations: action.congregations,
      };
    case TERRITORIES_LOADED:
      return {
        ...state,
        territories: action.territories,
      };
    case REGIONS_LOADED:
      return {
        ...state,
        regions: action.regions,
      };
    default:
      (action: empty); // type check that all action types are handled
      return state;
  }
};
