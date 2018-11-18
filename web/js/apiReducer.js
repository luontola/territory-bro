// Copyright © 2015-2018 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import type {ApiAction} from "./apiActions";
import {CONFIGURED, MY_CONGREGATIONS_LOADED, REGIONS_LOADED, TERRITORIES_LOADED, USER_LOGGED_IN} from "./apiActions";
import type {Congregation, Region, Territory} from "./api";

export type ApiState = {
  +dev: boolean,
  +auth0Domain: string,
  +auth0ClientId: string,
  +supportEmail: string,
  +authenticated: boolean,
  +userId: ?string,
  +userFullName: ?string,
  +congregations: Array<Congregation>,
  +territories: Array<Territory>,
  +regions: Array<Region>,
};

const defaultState: ApiState = {
  dev: false,
  auth0Domain: '',
  auth0ClientId: '',
  supportEmail: '',
  authenticated: false,
  userId: null,
  userFullName: null,
  congregations: [],
  territories: [],
  regions: [],
};

export default (state: ApiState = defaultState, action: ApiAction): ApiState => {
  switch (action.type) {
    case CONFIGURED:
      return {
        ...state,
        dev: action.dev,
        auth0Domain: action.auth0Domain,
        auth0ClientId: action.auth0ClientId,
        supportEmail: action.supportEmail,
      };
    case USER_LOGGED_IN:
      return {
        ...state,
        authenticated: true,
        userId: action.userId,
        userFullName: action.fullName,
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
