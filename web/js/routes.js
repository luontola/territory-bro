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

const routes: Array<Route> = [
  {
    path: '/',
    async action() {
      const [territories, regions] = await Promise.all([getTerritories(), getRegions()]);
      return <OverviewPage territoryCount={territories.length} regionCount={regions.length}/>;
    }
  },
  {
    path: '/territory-cards',
    async action() {
      const [territories, regions] = await Promise.all([getTerritories(), getRegions()]);
      return <TerritoryCardsPage territories={territories} regions={regions}/>;
    }
  },
  {
    path: '/neighborhood-maps',
    async action() {
      const territories = await getTerritories();
      return <NeighborhoodCardsPage territories={territories}/>;
    }
  },
  {
    path: '/region-maps',
    async action() {
      const [territories, regions] = await Promise.all([getTerritories(), getRegions()]);
      return <RegionPrintoutsPage territories={territories} regions={regions}/>;
    }
  },
  {
    path: '/error',
    action: ({error}: { error: ErrorMessage }) => <ErrorPage error={error}/>
  },
];

export default routes;
