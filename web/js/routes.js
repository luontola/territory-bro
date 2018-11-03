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
import {getMyCongregations, getRegions, getTerritories} from "./api";
import type {ErrorMessage, Route} from "./router";
import {myCongregationsLoaded, regionsLoaded, territoriesLoaded} from "./apiActions";
import RuralTerritoryCardsPage from "./pages/RuralTerritoryCardsPage";
import type {State} from "./reducers";
import {changeCongregation} from "./congregation";

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
    path: '/error',
    action: ({error}: { error: ErrorMessage }) => <ErrorPage error={error}/>
  },
];

export default routes;

async function fetchAll(store) {
  await Promise.all([
    fetchMyCongregations(store),
    fetchTerritories(store),
    fetchRegions(store)
  ]);
}


async function fetchMyCongregations(store) {
  const congregations = await getMyCongregations();
  store.dispatch(myCongregationsLoaded(congregations));

  const state: State = store.getState();
  if (state.config.congregationId === null && state.api.congregations.length > 0) {
    changeCongregation(state.api.congregations[0].id);
  }
}

async function fetchTerritories(store) {
  const state: State = store.getState();
  const territories = await getTerritories(state.config.congregationId);
  store.dispatch(territoriesLoaded(territories));
}

async function fetchRegions(store) {
  const state: State = store.getState();
  const regions = await getRegions(state.config.congregationId);
  store.dispatch(regionsLoaded(regions));
}

