// Copyright © 2015-2017 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import React from "react";
import Layout from "./Layout";
import type {Region, Territory} from "../api";
import PrintOptionsForm, {getMapRaster} from "../prints/PrintOptionsForm";
import {connect} from "react-redux";
import type {MapRaster} from "../maps/mapOptions";
import RegionPrintout from "../prints/RegionPrintout";

let RegionPrintoutsPage = ({territories, regions, mapRaster}: {
  territories: Array<Territory>,
  regions: Array<Region>,
  mapRaster: MapRaster,
}) => (
  <Layout>
    <div className="no-print">
      <h1>Region Maps</h1>
      <PrintOptionsForm territories={territories} regions={regions}/>
    </div>
    {regions
      .filter(region => region.congregation || region.subregion)
      .map(region =>
        <RegionPrintout key={region.id} region={region} territories={territories} mapRaster={mapRaster}/>
      )}
  </Layout>
);

function mapStateToProps(state) {
  return {
    mapRaster: getMapRaster(state),
  };
}

RegionPrintoutsPage = connect(mapStateToProps)(RegionPrintoutsPage);

export default RegionPrintoutsPage;
