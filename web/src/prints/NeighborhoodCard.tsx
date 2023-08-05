// Copyright © 2015-2023 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import {useCongregationById} from "../api";
import NeighborhoodMap from "../maps/NeighborhoodMap";
import CropMarks from "./CropMarks";
import styles from "./NeighborhoodCard.module.css";
import {findMapRasterById} from "../maps/mapOptions.ts";
import {memo} from "react";

const NeighborhoodCard = ({
                            territory,
                            territoryId,
                            congregation,
                            congregationId,
                            mapRaster,
                            mapRasterId
                          }) => {
  congregation = congregation || useCongregationById(congregationId);
  territory = territory || congregation.getTerritoryById(territoryId);
  mapRaster = mapRaster || findMapRasterById(mapRasterId);
  return <CropMarks>
    <div className={styles.root}>
      <NeighborhoodMap territory={territory} mapRaster={mapRaster} printout={true}/>
    </div>
  </CropMarks>;
};

export default memo(NeighborhoodCard);
