// Copyright © 2015-2023 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import {api} from "./util";
import alphanumSort from "alphanum-sort";
import {findIndex, keyBy, sortBy} from "lodash-es";
import WKT from "ol/format/WKT";
import MultiPolygon from "ol/geom/MultiPolygon";
import {QueryClient, useQuery, UseQueryResult} from '@tanstack/react-query'

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      suspense: true,
      staleTime: Infinity,
    },
  },
});

// ====== Settings & Authentication ======

export type User = {
  id: string;
  sub?: string;
  name?: string;
  nickname?: string;
  email?: string;
  emailVerified?: boolean;
  picture?: string;
};

export type Settings = {
  dev: boolean;
  auth0: {
    domain: string;
    clientId: string;
  };
  supportEmail: string;
  demoAvailable: boolean;
  user?: User;
};

export function getSettings(): Settings {
  const {data} = useQuery({
    queryKey: ['settings'],
    queryFn: async () => {
      const response = await api.get('/api/settings');
      return response.data;
    }
  });
  return data;
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

function sortUsers(users: Array<User>): Array<User> {
  return sortBy(users, [u => (u.name || '').toLowerCase(), 'id']);
}


// ====== Congregations ======

export type Congregation = {
  id: string;
  name: string;
  permissions: {
    [permission: string]: boolean;
  };
  loansCsvUrl?: string;
  location: string;
  congregationBoundaries: Array<Boundary>;
  territories: Array<Territory>;
  getTerritoryById: (id: string) => Territory;
  regions: Array<Region>;
  getRegionById: (id: string) => Region;
  cardMinimapViewports: Array<Viewport>;
  users: Array<User>;
};

function sortCongregations(congregations: Array<Congregation>): Array<Congregation> {
  return sortBy(congregations, c => c.name.toLowerCase());
}

export function enrichCongregation(congregation): Congregation {
  congregation.users = sortUsers(congregation.users);
  congregation.territories = sortTerritories(congregation.territories);
  congregation.regions = sortRegions(congregation.regions);
  congregation.territories.forEach(territory => {
    territory.enclosingRegion = getEnclosing(territory.location, congregation.regions.map(region => region.location));
    territory.enclosingMinimapViewport = getEnclosing(territory.location, congregation.cardMinimapViewports.map(viewport => viewport.location));
  });
  congregation.location = mergeMultiPolygons(congregation.congregationBoundaries.map(boundary => boundary.location)) || "MULTIPOLYGON(((180 90,180 -90,-180 -90,-180 90,180 90)))";

  const territoriesById = keyBy(congregation.territories, 'id');
  congregation.getTerritoryById = id => territoriesById[id] || (() => {
    throw Error(`Territory not found: ${id}`);
  })();

  const regionsById = keyBy(congregation.regions, 'id');
  congregation.getRegionById = id => regionsById[id] || (() => {
    throw Error(`Region not found: ${id}`);
  })();

  return congregation;
}

function getEnclosing(innerWkt: string, enclosingCandidateWkts: Array<string>): string | null {
  const wkt = new WKT();
  // TODO: sort by overlapping area instead of center point
  const centerPoint = wkt.readFeature(innerWkt).getGeometry().getInteriorPoints().getPoint(0);
  const territoryCoordinate = centerPoint.getCoordinates();

  let result = null;
  enclosingCandidateWkts.forEach(enclosing => {
    if (wkt.readFeature(enclosing).getGeometry().intersectsCoordinate(territoryCoordinate)) {
      result = enclosing;
    }
  });
  return result;
}

function mergeMultiPolygons(multiPolygons: Array<string>): string | null {
  if (multiPolygons.length === 0) {
    return null;
  }
  const wkt = new WKT();
  const merged = multiPolygons.map(p => wkt.readFeature(p).getGeometry()).reduce((a, b) => new MultiPolygon([...a.getPolygons(), ...b.getPolygons()]));
  return wkt.writeGeometry(merged);
}

export function getCongregations(): Array<Congregation> {
  const {data} = useQuery({
    queryKey: ['congregations'],
    queryFn: async () => {
      const response = await api.get('/api/congregations');
      return sortCongregations(response.data);
    }
  })
  return data as Array<Congregation>;
}

export function getCongregationById(congregationId: string): Congregation {
  const {data} = useQuery({
    queryKey: ['congregation', congregationId],
    queryFn: async () => {
      const response = await api.get(`/api/congregation/${congregationId}`);
      return enrichCongregation(response.data);
    }
  })
  return data as Congregation;
}

export async function createCongregation(name: string) {
  const response = await api.post('/api/congregations', {name});
  await queryClient.invalidateQueries({queryKey: ['congregations']});
  return response.data.id;
}

export async function addUser(congregationId: string, userId: string) {
  await api.post(`/api/congregation/${congregationId}/add-user`, {userId});
  await queryClient.invalidateQueries({queryKey: ['congregation', congregationId]});
}

export async function setUserPermissions(congregationId: string, userId: string, permissions: Array<string>, isCurrentUser: boolean) {
  await api.post(`/api/congregation/${congregationId}/set-user-permissions`, {userId, permissions});
  const congregationRefreshed = queryClient.invalidateQueries({queryKey: ['congregation', congregationId]});
  if (isCurrentUser) {
    // The user may have removed themselves from the congregation. This affects the list of all congregations.
    // More importantly, they will no longer be able to read the current congregation, so refreshing it will fail.
    // We must not await for the congregation to be refreshed, because React Query would only retry it multiple times
    // before giving up, which is slow.
    await queryClient.invalidateQueries({queryKey: ['congregations']});
  } else {
    await congregationRefreshed;
  }
}

export async function saveCongregationSettings(congregationId: string, congregationName: string, loansCsvUrl) {
  await api.post(`/api/congregation/${congregationId}/settings`, {congregationName, loansCsvUrl});
  await queryClient.invalidateQueries({queryKey: ['congregation', congregationId]});
  await queryClient.invalidateQueries({queryKey: ['congregations']}); // in case congregation name changed
}


// ====== Territories ======

export type Territory = {
  id: string;
  number: string;
  addresses: string;
  region: string;
  location: string;
  loaned?: boolean;
  staleness?: number;
  meta: { [key: string]: any };
  enclosingRegion?: string;
  enclosingMinimapViewport?: string;
};

function sortTerritories(territories: Array<Territory>): Array<Territory> {
  const numbers: Array<string> = alphanumSort(territories.map(t => t.number));
  return sortBy(territories, t => findIndex(numbers, n => n === t.number));
}

export async function shareTerritory(congregationId: string, territoryId: string) {
  const response = await api.post(`/api/congregation/${congregationId}/territory/${territoryId}/share`, {});
  return response.data.url;
}

export async function openShare(shareKey: string) {
  const response = await api.get(`/api/share/${shareKey}`);
  return response.data;
}

export type QrCodeShare = {
  territory: string;
  key: string;
  url: string;
};

export function generateQrCodes(congregationId: string, territoryIds: Array<string>): UseQueryResult<Array<QrCodeShare>, unknown> {
  return useQuery({
    queryKey: ['congregation', congregationId, territoryIds],
    queryFn: async () => {
      const response = await api.post(`/api/congregation/${congregationId}/generate-qr-codes`, {territories: territoryIds})
      return response.data.qrCodes;
    },
    suspense: false,
  })
}

// ====== Regions ======

export type Region = {
  id: string;
  name: string;
  location: string;
};

function sortRegions(regions: Array<Region>): Array<Region> {
  return sortBy(regions, (r: Region) => r.name.toLowerCase());
}

export type Boundary = {
  id: string;
  location: string;
};

export type Viewport = {
  id: string;
  location: string;
};
