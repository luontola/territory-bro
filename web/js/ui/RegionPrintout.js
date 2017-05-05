// Copyright © 2015-2017 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import "../../css/territory-cards.css";
import React from "react";
import type {Region, Territory} from "../api";
import RegionMap from "./RegionMap";
import type {MapRaster} from "../maps";

const RegionPrintout = ({region, territories, mapRaster}: {
  region: Region,
  territories: Array<Territory>,
  mapRaster: MapRaster,
}) => (
  <div className="region-page crop-area">
    <div className="name">{region.name}</div>
    <div className="region-map">
      <RegionMap region={region} territories={territories} mapRaster={mapRaster}/>
    </div>
  </div>
);

export default RegionPrintout;
