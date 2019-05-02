// Copyright © 2015-2019 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import React from "react";
import type {MapRaster} from "../maps/mapOptions";
import type {Territory} from "../api";
import PrintOptionsForm, {getSelectedMapRaster, getSelectedTerritories} from "../prints/PrintOptionsForm";
import {connect} from "react-redux";
import NeighborhoodCard from "../prints/NeighborhoodCard";
import type {State} from "../reducers";

let NeighborhoodCardsPage = ({selectedTerritories, mapRaster}: {
  selectedTerritories: Array<Territory>,
  mapRaster: MapRaster,
}) => (
  <>
    <div className="no-print">
      <h1>Neighborhood Maps</h1>
      <PrintOptionsForm territoriesVisible={true}/>
    </div>
    {selectedTerritories.map(territory =>
      <NeighborhoodCard key={territory.id} territory={territory} mapRaster={mapRaster}/>
    )}
  </>
);

function mapStateToProps(state: State) {
  return {
    selectedTerritories: getSelectedTerritories(state),
    mapRaster: getSelectedMapRaster(state),
  };
}

NeighborhoodCardsPage = connect(mapStateToProps)(NeighborhoodCardsPage);

export default NeighborhoodCardsPage;
