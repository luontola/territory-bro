// Copyright © 2015-2023 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import {useCongregationById} from "../api";
import RegionMap from "../maps/RegionMap";
import A4PrintFrame from "./A4PrintFrame";
import styles from "./RegionPrintout.module.css";
import {findMapRasterById} from "../maps/mapOptions.ts";
import {memo} from "react";

const RegionPrintout = ({
                          region,
                          regionId,
                          congregation,
                          congregationId,
                          mapRaster,
                          mapRasterId
                        }) => {
  congregation = congregation || useCongregationById(congregationId);
  region = region || (regionId === congregationId ? congregation : congregation.getRegionById(regionId));
  mapRaster = mapRaster || findMapRasterById(mapRasterId);
  return <A4PrintFrame>
    <div className={styles.root}>
      <div className={styles.name}>{region.name}</div>
      <div className={styles.map}>
        <RegionMap region={region} territories={congregation.territories} mapRaster={mapRaster} printout={true}/>
      </div>
    </div>
  </A4PrintFrame>;
};

export default memo(RegionPrintout);
