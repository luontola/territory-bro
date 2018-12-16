// Copyright © 2015-2018 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import React from "react";
import ErrorPage from "./pages/ErrorPage";
import OverviewPage from "./pages/OverviewPage";
import TerritoryCardsPage from "./pages/TerritoryCardsPage";
import NeighborhoodCardsPage from "./pages/NeighborhoodCardsPage";
import RegionPrintoutsPage from "./pages/RegionPrintoutsPage";
import {getRegions, getSettings, getTerritories} from "./api";
import type {ErrorMessage, Route} from "./router";
import {configured, myCongregationsLoaded, regionsLoaded, territoriesLoaded, userLoggedIn} from "./apiActions";
import RuralTerritoryCardsPage from "./pages/RuralTerritoryCardsPage";
import type {State} from "./reducers";
import {saveCongregationId, savedCongregationId} from "./congregation";
import {buildAuthenticator} from "./authentication";
import RegistrationPage from "./pages/RegistrationPage";
import {congregationChanged} from "./configActions";

const routes: Array<Route> = [
  {
    path: '/',
    async action({store}) {
      await fetchAll(store);
      return <OverviewPage/>;
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
    async action({store}) {
      await fetchSettings(store);
      const state = store.getState();
      if (state.api.authenticated) {
        return <RegistrationPage/>
      } else {
        const auth0Domain = state.api.auth0Domain;
        const auth0ClientId = state.api.auth0ClientId;
        const auth = buildAuthenticator(auth0Domain, auth0ClientId);
        auth.login();
        return <p>Please wait, you will be redirected...</p>
      }
    }
  },
  {
    path: '/login-callback',
    async action({store}) {
      let state: State = store.getState();
      if (!(state.api.auth0Domain && state.api.auth0ClientId)) {
        await fetchSettings(store);
        state = store.getState();
      }
      const auth = buildAuthenticator(state.api.auth0Domain, state.api.auth0ClientId);
      await auth.handleAuthentication();
      const params = new URLSearchParams(document.location.search.substring(1));
      throw {
        redirect: {pathname: params.get('return') || '/'},
        replace: true,
      };
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
  const settings = await getSettings();
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
  const territories = await getTerritories(state.config.congregationId);
  store.dispatch(territoriesLoaded(territories));
}

async function fetchRegions(store) {
  const state: State = store.getState();
  if (!state.api.authenticated) {
    return;
  }
  const regions = await getRegions(state.config.congregationId);
  store.dispatch(regionsLoaded(regions));
}

