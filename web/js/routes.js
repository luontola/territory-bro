// Copyright © 2015-2019 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import React from "react";
import ErrorPage from "./pages/ErrorPage";
import OverviewPage from "./pages/OverviewPage";
import TerritoryCardsPage from "./pages/TerritoryCardsPage";
import NeighborhoodCardsPage from "./pages/NeighborhoodCardsPage";
import RegionPrintoutsPage from "./pages/RegionPrintoutsPage";
import {getRegions, getSettings_legacy, getTerritories} from "./api";
import type {ErrorMessage, Route} from "./router";
import {configured, myCongregationsLoaded, regionsLoaded, territoriesLoaded, userLoggedIn} from "./apiActions";
import RuralTerritoryCardsPage from "./pages/RuralTerritoryCardsPage";
import type {State} from "./reducers";
import {saveCongregationId, savedCongregationId} from "./congregation";
import RegistrationPage from "./pages/RegistrationPage";
import {congregationChanged} from "./configActions";
import CongregationPage from "./pages/CongregationPage";
import LoginCallbackPage from "./pages/LoginCallbackPage";

const routes: Array<Route> = [
  {
    path: '/',
    async action() {
      return <OverviewPage/>;
    }
  },
  {
    path: '/congregation/:congregationId',
    async action({params}) {
      return <CongregationPage congregationId={params.congregationId}/>;
    }
  },
  {
    path: '/territory-cards',
    async action({store}) {
      await fetchAll(store);
      return <TerritoryCardsPage/>;
    }
  },
  {
    path: '/neighborhood-maps',
    async action({store}) {
      await fetchAll(store);
      return <NeighborhoodCardsPage/>;
    }
  },
  {
    path: '/rural-territory-cards',
    async action({store}) {
      await fetchAll(store);
      return <RuralTerritoryCardsPage/>;
    }
  },
  {
    path: '/region-maps',
    async action({store}) {
      await fetchAll(store);
      return <RegionPrintoutsPage/>;
    }
  },
  {
    path: '/register',
    async action() {
      return <RegistrationPage/>;
    }
  },
  {
    path: '/login-callback',
    async action() {
      return <LoginCallbackPage/>;
    }
  },
  {
    path: '/error',
    action: ({error}: { error: ErrorMessage }) => <ErrorPage error={error}/>
  },
];

export default routes;

async function fetchAll(store) {
  await fetchSettings(store);
  await Promise.all([
    fetchTerritories(store),
    fetchRegions(store)
  ]);
}


async function fetchSettings(store) {
  const settings = await getSettings_legacy();
  store.dispatch(configured(settings.dev, settings.auth0.domain, settings.auth0.clientId, settings.supportEmail));
  if (settings.user.authenticated) {
    store.dispatch(userLoggedIn(settings.user.sub || '', settings.user.name || ''));
  }
  store.dispatch(myCongregationsLoaded(settings.congregations));

  const previousCongregationId = savedCongregationId();
  const congregation = settings.congregations.find(c => c.id === previousCongregationId) || settings.congregations[0];
  if (congregation) {
    saveCongregationId(congregation.id);
    store.dispatch(congregationChanged(congregation.id));
  }
}

async function fetchTerritories(store) {
  const state: State = store.getState();
  if (!state.api.authenticated) {
    return;
  }
  if (state.config.congregationId) {
    const territories = await getTerritories(state.config.congregationId);
    store.dispatch(territoriesLoaded(territories));
  }
}

async function fetchRegions(store) {
  const state: State = store.getState();
  if (!state.api.authenticated) {
    return;
  }
  if (state.config.congregationId) {
    const regions = await getRegions(state.config.congregationId);
    store.dispatch(regionsLoaded(regions));
  }
}
