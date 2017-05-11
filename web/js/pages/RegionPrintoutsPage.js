// Copyright © 2015-2017 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import React from "react";
import Layout from "./Layout";
import type {Region, Territory} from "../api";
import PrintOptionsForm, {filterSelectedRegions, getSelectedMapRaster} from "../prints/PrintOptionsForm";
import {connect} from "react-redux";
import type {MapRaster} from "../maps/mapOptions";
import RegionPrintout from "../prints/RegionPrintout";

let RegionPrintoutsPage = ({territories, regions, selectedRegions, mapRaster}: {
  territories: Array<Territory>,
  regions: Array<Region>,
  selectedRegions: Array<Region>,
  mapRaster: MapRaster,
}) => (
  <Layout>
    <div className="no-print">
      <h1>Region Maps</h1>
      <PrintOptionsForm territories={territories} regions={regions}/>
    </div>
    {selectedRegions.map(region =>
      <RegionPrintout key={region.id} region={region} territories={territories} mapRaster={mapRaster}/>
    )}
  </Layout>
);

function mapStateToProps(state, ownProps) {
  return {
    selectedRegions: filterSelectedRegions(state, ownProps.regions),
    mapRaster: getSelectedMapRaster(state),
  };
}

RegionPrintoutsPage = connect(mapStateToProps)(RegionPrintoutsPage);

export default RegionPrintoutsPage;
