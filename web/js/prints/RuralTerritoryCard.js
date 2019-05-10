// Copyright © 2015-2019 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import React from "react";
import {FormattedMessage} from "react-intl";
import TerritoryMap from "../maps/TerritoryMap";
import TerritoryMiniMap from "../maps/TerritoryMiniMap";
import type {Territory} from "../api";
import {getCongregationById} from "../api";
import type {MapRaster} from "../maps/mapOptions";
import styles from "./RuralTerritoryCard.css";
import A5PrintFrame from "./A5PrintFrame";
import PrintDateNotice from "./PrintDateNotice";

const RuralTerritoryCard = ({territory, congregationId, mapRaster}: {
  territory: Territory,
  congregationId: string,
  mapRaster: MapRaster
}) => {
  const congregation = getCongregationById(congregationId);
  return (
    <A5PrintFrame>
      <div className={styles.root}>

        <div className={styles.minimap}>
          <TerritoryMiniMap territory={territory} congregation={congregation}/>
        </div>

        <div className={styles.header}>
          <div className={styles.title}>
            <FormattedMessage id="TerritoryCard.title"/>
          </div>
          <div className={styles.subregion}>
            {territory.subregion}
          </div>
        </div>

        <div className={styles.number}>
          {territory.number}
        </div>

        <div className={styles.map}>
          <PrintDateNotice>
            <TerritoryMap territory={territory} mapRaster={mapRaster}/>
          </PrintDateNotice>
        </div>

      </div>
    </A5PrintFrame>
  );
};

export default RuralTerritoryCard;
