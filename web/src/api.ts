// Copyright © 2015-2024 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

export type User = {
  id: string;
  sub?: string;
  name?: string;
  nickname?: string;
  email?: string;
  emailVerified?: boolean;
  picture?: string;
};

export type Congregation = {
  id: string;
  name: string;
  permissions: {
    [permission: string]: boolean;
  };
  loansCsvUrl?: string;
  location: string;
  congregationBoundaries: Boundary[];
  territories: Territory[];
  getTerritoryById: (id: string) => Territory;
  regions: Region[];
  getRegionById: (id: string) => Region;
  cardMinimapViewports: Viewport[];
  users: User[];
};

export type Territory = {
  id: string;
  number: string;
  addresses: string;
  region: string;
  location: string;
  doNotCalls?: string;
  loaned?: boolean;
  staleness?: number;
  meta: { [key: string]: any };
  enclosingRegion?: string;
  enclosingMinimapViewport?: string;
};

export type Region = {
  id: string;
  name: string;
  location: string;
};

export type Boundary = {
  id: string;
  location: string;
};

export type Viewport = {
  id: string;
  location: string;
};
