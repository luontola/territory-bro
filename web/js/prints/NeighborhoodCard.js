// Copyright © 2015-2019 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import React from "react";
import type {MapRaster} from "../maps/mapOptions";
import {getCongregationById} from "../api";
import NeighborhoodMap from "../maps/NeighborhoodMap";
import CropMarks from "./CropMarks";
import styles from "./NeighborhoodCard.css";

const NeighborhoodCard = function ({territoryId, congregationId, mapRaster}: {
  territoryId: string,
  congregationId: string,
  mapRaster: MapRaster
}) {
  const congregation = getCongregationById(congregationId);
  const territory = congregation.getTerritoryById(territoryId);
  return (
    <CropMarks>
      <div className={styles.root}>
        <NeighborhoodMap territory={territory} mapRaster={mapRaster}/>
      </div>
    </CropMarks>
  );
};

export default NeighborhoodCard;
