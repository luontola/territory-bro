// Copyright © 2015-2019 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import React from "react";
import type {Subregion, Territory} from "../api";
import {getCongregationById} from "../api";
import RegionMap from "../maps/RegionMap";
import type {MapRaster} from "../maps/mapOptions";
import A4PrintFrame from "./A4PrintFrame";
import styles from "./RegionPrintout.css";

const RegionPrintout = ({congregationId, region, territories, mapRaster}: {
  congregationId: string,
  region: Subregion,
  territories: Array<Territory>,
  mapRaster: MapRaster,
}) => {
  const congregation = getCongregationById(congregationId);
  return (
    <A4PrintFrame>
      <div className={styles.root}>
        <div className={styles.name}>{region.name}</div>
        <div className={styles.map}>
          <RegionMap region={region} territories={territories} congregationId={congregationId} mapRaster={mapRaster}/>
        </div>
      </div>
    </A4PrintFrame>
  );
};

export default RegionPrintout;
