// Copyright © 2015-2024 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import {api} from "./util";
import alphanumSort from "alphanum-sort";
import {findIndex, keyBy, sortBy} from "lodash-es";
import WKT from "ol/format/WKT";
import MultiPolygon from "ol/geom/MultiPolygon";
import {QueryClient, useQuery, useSuspenseQuery} from '@tanstack/react-query'
import {axiosHttpStatus} from "./pages/ErrorPage.tsx";

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: Infinity,
      retry: (failureCount, error) => {
        const httpStatus = axiosHttpStatus(error);
        if (httpStatus === 401 || httpStatus === 403) {
          // let ErrorPage handle authentication issues - retrying won't help
          return false;
        }
        return failureCount < 3;
      }
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

const gitCommit = import.meta.env.VITE_GIT_COMMIT || 'dev';
const settingsUrl = `/api/settings?v=${gitCommit}`;

export function useSettings(): Settings {
  const {data} = useSuspenseQuery({
    queryKey: ['settings'],
    queryFn: async () => {
      const response = await api.get(settingsUrl);
      return response.data as Settings;
    }
  });
  return data;
}

export function useSettingsSafe() {
  return useQuery({
    queryKey: ['settings'],
    queryFn: async () => {
      const response = await api.get(settingsUrl);
      return response.data as Settings;
    }
  });
}

export async function loginWithIdToken(idToken: string) {
  await api.post('/api/login', {idToken});
  await queryClient.invalidateQueries();
}

export async function devLogin() {
  await api.post('/api/dev-login', {sub: "developer", name: "Developer", email: "developer@example.com"});
  await queryClient.invalidateQueries();
}

export async function logout() {
  await api.post('/api/logout');
  // Full page reload instead of invalidateQueries() to avoid HTTP 401 responses,
  // if React Query decides to re-fetch the currently shown congregation, which
  // could trigger a new login right after logging out.
  window.location.href = '/';
}

function sortUsers(users: User[]): User[] {
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
  congregationBoundaries: Boundary[];
  territories: Territory[];
  getTerritoryById: (id: string) => Territory;
  regions: Region[];
  getRegionById: (id: string) => Region;
  cardMinimapViewports: Viewport[];
  users: User[];
};

function sortCongregations(congregations: Congregation[]): Congregation[] {
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

function getEnclosing(innerWkt: string, enclosingCandidateWkts: string[]): string | null {
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

function mergeMultiPolygons(multiPolygons: string[]): string | null {
  if (multiPolygons.length === 0) {
    return null;
  }
  const wkt = new WKT();
  const merged = multiPolygons.map(p => wkt.readFeature(p).getGeometry()).reduce((a, b) => new MultiPolygon([...a.getPolygons(), ...b.getPolygons()]));
  return wkt.writeGeometry(merged);
}

export function useCongregations(): Congregation[] {
  const {data} = useSuspenseQuery({
    queryKey: ['congregations'],
    queryFn: async () => {
      const response = await api.get('/api/congregations');
      return sortCongregations(response.data);
    }
  })
  return data;
}

export function useCongregationById(congregationId: string): Congregation {
  const {data} = useSuspenseQuery({
    queryKey: ['congregation', congregationId],
    queryFn: async () => {
      const response = await api.get(`/api/congregation/${congregationId}`);
      return enrichCongregation(response.data);
    }
  })
  return data;
}

export function useTerritoryById(congregationId: string, territoryId: string): Territory {
  const {data} = useSuspenseQuery({
    queryKey: ['territory', congregationId, territoryId],
    queryFn: async () => {
      const response = await api.get(`/api/congregation/${congregationId}/territory/${territoryId}`);
      return response.data as Territory;
    }
  })
  return data;
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

export async function setUserPermissions(congregationId: string, userId: string, permissions: string[], isCurrentUser: boolean) {
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

function sortTerritories(territories: Territory[]): Territory[] {
  const numbers: string[] = alphanumSort(territories.map(t => t.number));
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

export function useGeneratedQrCodes(congregationId: string, territoryIds: string[], enabled = true) {
  return useQuery({
    queryKey: ['congregation', congregationId, territoryIds],
    queryFn: async () => {
      const response = await api.post(`/api/congregation/${congregationId}/generate-qr-codes`, {territories: territoryIds})
      return response.data.qrCodes as QrCodeShare[];
    },
    enabled,
  })
}

// ====== Regions ======

export type Region = {
  id: string;
  name: string;
  location: string;
};

function sortRegions(regions: Region[]): Region[] {
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
