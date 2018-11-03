// Copyright © 2015-2018 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import React from "react";
import Layout from "../layout/Layout";
import type {MapRaster} from "../maps/mapOptions";
import type {Region, Territory} from "../api";
import PrintOptionsForm, {getSelectedMapRaster, getSelectedTerritories} from "../prints/PrintOptionsForm";
import {connect} from "react-redux";
import type {State} from "../reducers";
import RuralTerritoryCard from "../prints/RuralTerritoryCard";

const title = "Rural Territory Cards";

let RuralTerritoryCardsPage = ({allRegions, selectedTerritories, mapRaster}: {
  allRegions: Array<Region>,
  selectedTerritories: Array<Territory>,
  mapRaster: MapRaster,
}) => (
  <Layout title={title}>
    <div className="no-print">
      <h1>{title}</h1>
      <PrintOptionsForm territoriesVisible={true}/>
    </div>
    {selectedTerritories.map(territory =>
      <RuralTerritoryCard key={territory.id} territory={territory} regions={allRegions} mapRaster={mapRaster}/>
    )}
  </Layout>
);

function mapStateToProps(state: State) {
  return {
    allRegions: state.api.regions,
    selectedTerritories: getSelectedTerritories(state),
    mapRaster: getSelectedMapRaster(state),
  };
}

RuralTerritoryCardsPage = connect(mapStateToProps)(RuralTerritoryCardsPage);

export default RuralTerritoryCardsPage;
