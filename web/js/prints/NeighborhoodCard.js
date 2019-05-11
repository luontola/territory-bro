// Copyright © 2015-2019 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import React from "react";
import {getCongregationById} from "../api";
import NeighborhoodMap from "../maps/NeighborhoodMap";
import CropMarks from "./CropMarks";
import styles from "./NeighborhoodCard.css";

const NeighborhoodCard = ({territory, territoryId, congregation, congregationId, mapRaster}) => {
  congregation = congregation || getCongregationById(congregationId);
  territory = territory || congregation.getTerritoryById(territoryId);
  return (
    <CropMarks>
      <div className={styles.root}>
        <NeighborhoodMap territory={territory} mapRaster={mapRaster}/>
      </div>
    </CropMarks>
  );
};

export default NeighborhoodCard;
