// Copyright © 2015-2017 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import React from "react";
import Layout from "./Layout";
import type {Region, Territory} from "../api";
import PrintOptionsForm, {getSelectedMapRaster, getSelectedRegions} from "../prints/PrintOptionsForm";
import {connect} from "react-redux";
import type {MapRaster} from "../maps/mapOptions";
import RegionPrintout from "../prints/RegionPrintout";
import type {State} from "../reducers";

let RegionPrintoutsPage = ({allTerritories, selectedRegions, mapRaster}: {
  allTerritories: Array<Territory>,
  selectedRegions: Array<Region>,
  mapRaster: MapRaster,
}) => (
  <Layout>
    <div className="no-print">
      <h1>Region Maps</h1>
      <PrintOptionsForm regionsVisible={true}/>
    </div>
    {selectedRegions.map(region =>
      <RegionPrintout key={region.id} region={region} territories={allTerritories} mapRaster={mapRaster}/>
    )}
  </Layout>
);

function mapStateToProps(state: State) {
  return {
    allTerritories: state.api.territories,
    selectedRegions: getSelectedRegions(state),
    mapRaster: getSelectedMapRaster(state),
  };
}

RegionPrintoutsPage = connect(mapStateToProps)(RegionPrintoutsPage);

export default RegionPrintoutsPage;
