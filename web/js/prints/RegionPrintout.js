// Copyright © 2015-2019 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import React from "react";
import {getCongregationById} from "../api";
import RegionMap from "../maps/RegionMap";
import type {MapRaster} from "../maps/mapOptions";
import A4PrintFrame from "./A4PrintFrame";
import styles from "./RegionPrintout.css";

const RegionPrintout = ({regionId, congregationId, mapRaster}: {
  regionId: string,
  congregationId: string,
  mapRaster: MapRaster,
}) => {
  const congregation = getCongregationById(congregationId);
  const region = regionId === congregationId ? congregation
    : congregation.getSubregionById(regionId);
  return (
    <A4PrintFrame>
      <div className={styles.root}>
        <div className={styles.name}>{region.name}</div>
        <div className={styles.map}>
          <RegionMap region={region} territories={congregation.territories} mapRaster={mapRaster}/>
        </div>
      </div>
    </A4PrintFrame>
  );
};

export default RegionPrintout;
