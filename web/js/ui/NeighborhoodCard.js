// Copyright © 2015-2017 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import React from "react";
import type {MapRaster} from "../maps";
import type {Territory} from "../api";
import NeighborhoodMap from "./NeighborhoodMap";
import CropMarks from "./CropMarks";
import styles from "./NeighborhoodCard.css";

const NeighborhoodCard = ({territory, mapRaster}: {
  territory: Territory,
  mapRaster: MapRaster,
}) => (
  <CropMarks className={styles.root}>
    <NeighborhoodMap territory={territory} mapRaster={mapRaster}/>
  </CropMarks>
);

export default NeighborhoodCard;
