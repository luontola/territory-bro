// Copyright © 2015-2017 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import React from "react";
import Layout from "./Layout";
import type {MapRaster} from "../maps/mapOptions";
import type {Territory} from "../api";
import PrintOptionsForm, {getSelectedMapRaster, getSelectedTerritories} from "../prints/PrintOptionsForm";
import {connect} from "react-redux";
import NeighborhoodCard from "../prints/NeighborhoodCard";
import type {State} from "../reducers";

const title = "Neighborhood Maps";

let NeighborhoodCardsPage = ({selectedTerritories, mapRaster}: {
  selectedTerritories: Array<Territory>,
  mapRaster: MapRaster,
}) => (
  <Layout title={title}>
    <div className="no-print">
      <h1>{title}</h1>
      <PrintOptionsForm territoriesVisible={true}/>
    </div>
    {selectedTerritories.map(territory =>
      <NeighborhoodCard key={territory.id} territory={territory} mapRaster={mapRaster}/>
    )}
  </Layout>
);

function mapStateToProps(state: State) {
  return {
    selectedTerritories: getSelectedTerritories(state),
    mapRaster: getSelectedMapRaster(state),
  };
}

NeighborhoodCardsPage = connect(mapStateToProps)(NeighborhoodCardsPage);

export default NeighborhoodCardsPage;
