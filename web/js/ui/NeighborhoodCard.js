// Copyright © 2015-2017 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import "../../css/territory-cards.css";
import React from "react";
import type {MapRaster} from "../maps";
import type {Territory} from "../api";
import NeighborhoodMap from "./NeighborhoodMap";

const NeighborhoodCard = ({territory, mapRaster}: {
  territory: Territory,
  mapRaster: MapRaster,
}) => (
  <div className="croppable-territory-card">
    <div className="crop-mark-top-left"><img src="/img/crop-mark.svg" alt=""/></div>
    <div className="crop-mark-top-right"><img src="/img/crop-mark.svg" alt=""/></div>
    <div className="crop-area neighborhood-map">
      <NeighborhoodMap territory={territory} mapRaster={mapRaster}/>
    </div>
    <div className="crop-mark-bottom-left"><img src="/img/crop-mark.svg" alt=""/></div>
    <div className="crop-mark-bottom-right"><img src="/img/crop-mark.svg" alt=""/></div>
  </div>
);

export default NeighborhoodCard;
