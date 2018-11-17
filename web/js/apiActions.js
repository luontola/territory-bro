// Copyright © 2015-2018 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import type {Congregation, Region, Territory} from "./api";

export type ConfiguredAction = { type: 'CONFIGURED', auth0Domain: string, auth0ClientId: string, supportEmail: string };
export const CONFIGURED = 'CONFIGURED';
export const configured = (auth0Domain: string, auth0ClientId: string, supportEmail: string): ConfiguredAction => ({
  type: CONFIGURED,
  auth0Domain,
  auth0ClientId,
  supportEmail,
});

export type UserLoggedInAction = { type: 'USER_LOGGED_IN', userId: string, fullName: string };
export const USER_LOGGED_IN = 'USER_LOGGED_IN';
export const userLoggedIn = (userId: string, fullName: string): UserLoggedInAction => ({
  type: USER_LOGGED_IN,
  userId,
  fullName,
});

export type MyCongregationsLoadedAction = { type: 'MY_CONGREGATIONS_LOADED', congregations: Array<Congregation> };
export const MY_CONGREGATIONS_LOADED = 'MY_CONGREGATIONS_LOADED';
export const myCongregationsLoaded = (congregations: Array<Congregation>): MyCongregationsLoadedAction => ({
  type: MY_CONGREGATIONS_LOADED,
  congregations
});

export type TerritoriesLoadedAction = { type: 'TERRITORIES_LOADED', territories: Array<Territory> };
export const TERRITORIES_LOADED = 'TERRITORIES_LOADED';
export const territoriesLoaded = (territories: Array<Territory>): TerritoriesLoadedAction => ({
  type: TERRITORIES_LOADED,
  territories
});

export type RegionsLoadedAction = { type: 'REGIONS_LOADED', regions: Array<Region> };
export const REGIONS_LOADED = 'REGIONS_LOADED';
export const regionsLoaded = (regions: Array<Region>): RegionsLoadedAction => ({
  type: REGIONS_LOADED,
  regions
});

export type ApiAction =
  | ConfiguredAction
  | UserLoggedInAction
  | MyCongregationsLoadedAction
  | TerritoriesLoadedAction
  | RegionsLoadedAction;
