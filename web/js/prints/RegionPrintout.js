// Copyright © 2015-2017 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import React from "react";
import type {Region, Territory} from "../api";
import RegionMap from "../maps/RegionMap";
import type {MapRaster} from "../maps/mapOptions";
import A4PrintFrame from "./A4PrintFrame";
import styles from "./RegionPrintout.css";

const RegionPrintout = ({region, territories, mapRaster}: {
  region: Region,
  territories: Array<Territory>,
  mapRaster: MapRaster,
}) => (
  <A4PrintFrame>
    <div className={styles.root}>
      <div className={styles.name}>{region.name}</div>
      <div className={styles.map}>
        <RegionMap region={region} territories={territories} mapRaster={mapRaster}/>
      </div>
    </div>
  </A4PrintFrame>
);

export default RegionPrintout;
