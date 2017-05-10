// Copyright © 2015-2017 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import React from "react";
import Layout from "./Layout";
import type {MapRaster} from "../maps/mapOptions";
import type {Region, Territory} from "../api";
import PrintOptionsForm, {getMapRaster} from "../prints/PrintOptionsForm";
import {connect} from "react-redux";
import NeighborhoodCard from "../prints/NeighborhoodCard";

let NeighborhoodCardsPage = ({territories, regions, mapRaster}: {
  territories: Array<Territory>,
  regions: Array<Region>,
  mapRaster: MapRaster,
}) => (
  <Layout>
    <div className="no-print">
      <h1>Neighborhood Maps</h1>
      <PrintOptionsForm territories={territories} regions={regions}/>
    </div>
    {territories.map(territory =>
      <NeighborhoodCard key={territory.id} territory={territory} mapRaster={mapRaster}/>
    )}
  </Layout>
);

function mapStateToProps(state) {
  return {
    mapRaster: getMapRaster(state),
  };
}

NeighborhoodCardsPage = connect(mapStateToProps)(NeighborhoodCardsPage);

export default NeighborhoodCardsPage;
