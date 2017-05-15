// Copyright © 2015-2017 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import React from "react";
import ErrorPage from "./pages/ErrorPage";
import OverviewPage from "./pages/OverviewPage";
import TerritoryCardsPage from "./pages/TerritoryCardsPage";
import NeighborhoodCardsPage from "./pages/NeighborhoodCardsPage";
import RegionPrintoutsPage from "./pages/RegionPrintoutsPage";
import {getRegions, getTerritories} from "./api";
import type {ErrorMessage, Route} from "./router";
import {regionsLoaded, territoriesLoaded} from "./apiActions";
import RuralTerritoryCardsPage from "./pages/RuralTerritoryCardsPage";

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
    fetchTerritories(store),
    fetchRegions(store)
  ]);
}

async function fetchTerritories(store) {
  const territories = await getTerritories();
  store.dispatch(territoriesLoaded(territories));
}

async function fetchRegions(store) {
  const regions = await getRegions();
  store.dispatch(regionsLoaded(regions));
}

