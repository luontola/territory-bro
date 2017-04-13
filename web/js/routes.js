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
    action: () => <OverviewPage territoryCount={0} regionCount={0}/> // TODO: actual counts
  },
  {
    path: '/territory-cards',
    async action() {
      const [territories, regions] = await Promise.all([getTerritories(), getRegions()]);
      // TODO: remove debug logging
      console.log("territories", territories);
      console.log("regions", regions);
      return <TerritoryCardsPage territories={territories} regions={regions}/>;
    }
  },
  {
    path: '/neighborhood-maps',
    action: () => <NeighborhoodMapsPage />
  },
  {
    path: '/region-maps',
    action: () => <RegionMapsPage />
  },
  {
    path: '/error',
    action: ({error}) => <ErrorPage error={error}/>
  },
];

export default routes;
