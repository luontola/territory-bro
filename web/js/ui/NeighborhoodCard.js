// Copyright © 2015-2017 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import "../../css/territory-cards.css";
import React from "react";
import type {MapRaster} from "../maps";
import type {Territory} from "../api";
import NeighborhoodMap from "./NeighborhoodMap";
import CropMarks from "./CropMarks";

const NeighborhoodCard = ({territory, mapRaster}: {
  territory: Territory,
  mapRaster: MapRaster,
}) => (
  <CropMarks className="neighborhood-map">
    <NeighborhoodMap territory={territory} mapRaster={mapRaster}/>
  </CropMarks>
);

export default NeighborhoodCard;
