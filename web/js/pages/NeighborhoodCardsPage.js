// Copyright © 2015-2017 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import React from "react";
import Layout from "./Layout";
import type {MapRaster} from "../maps/mapOptions";
import type {Region, Territory} from "../api";
import PrintOptionsForm, {filterSelectedTerritories, getSelectedMapRaster} from "../prints/PrintOptionsForm";
import {connect} from "react-redux";
import NeighborhoodCard from "../prints/NeighborhoodCard";

let NeighborhoodCardsPage = ({territories, regions, selectedTerritories, mapRaster}: {
  territories: Array<Territory>,
  regions: Array<Region>,
  selectedTerritories: Array<Territory>,
  mapRaster: MapRaster,
}) => (
  <Layout>
    <div className="no-print">
      <h1>Neighborhood Maps</h1>
      <PrintOptionsForm territories={territories} regions={regions}/>
    </div>
    {selectedTerritories.map(territory =>
      <NeighborhoodCard key={territory.id} territory={territory} mapRaster={mapRaster}/>
    )}
  </Layout>
);

function mapStateToProps(state, ownProps) {
  return {
    selectedTerritories: filterSelectedTerritories(state, ownProps.territories),
    mapRaster: getSelectedMapRaster(state),
  };
}

NeighborhoodCardsPage = connect(mapStateToProps)(NeighborhoodCardsPage);

export default NeighborhoodCardsPage;
