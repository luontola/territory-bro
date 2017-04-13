// Copyright © 2015-2017 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import React from "react";
import {ErrorPage} from "./ui/ErrorPage";
import {OverviewPage} from "./ui/OverviewPage";
import {TerritoryCardsPage} from "./ui/TerritoryCardsPage";
import {NeighborhoodMapsPage} from "./ui/NeighborhoodMapsPage";
import {RegionMapsPage} from "./ui/RegionMapsPage";
import {getRegions, getTerritories} from "./api";

const routes = [
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
      return <NeighborhoodMapsPage territories={territories}/>;
    }
  },
  {
    path: '/region-maps',
    async action() {
      const [territories, regions] = await Promise.all([getTerritories(), getRegions()]);
      return <RegionMapsPage territories={territories} regions={regions}/>;
    }
  },
  {
    path: '/error',
    action: ({error}) => <ErrorPage error={error}/>
  },
];

export default routes;
