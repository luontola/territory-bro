// Copyright © 2015-2017 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import React from "react";
import formatDate from "date-fns/format";
import {FormattedMessage} from "react-intl";
import TerritoryMap from "../maps/TerritoryMap";
import TerritoryMiniMap from "../maps/TerritoryMiniMap";
import type {Region, Territory} from "../api";
import type {MapRaster} from "../maps/mapOptions";
import styles from "./RuralTerritoryCard.css";
import A5PrintFrame from "./A5PrintFrame";

const RuralTerritoryCard = ({territory, regions, mapRaster}: {
  territory: Territory,
  regions: Array<Region>,
  mapRaster: MapRaster
}) => {
  const today = formatDate(new Date(), 'YYYY-MM-DD');
  return (
    <A5PrintFrame>
      <div className={styles.root}>

        <div className={styles.number}>{territory.number}</div>

        <div className={styles.minimap}>
          <TerritoryMiniMap territory={territory} regions={regions}/>
        </div>

        <div className={styles.title}>
          <FormattedMessage id="TerritoryCard.title"/>
        </div>

        <div className={styles.region}>{territory.region}</div>

        <div className={styles.map}>
          <TerritoryMap territory={territory} mapRaster={mapRaster}/>
        </div>

        <div className={styles.disclaimer}>
          <div>Printed {today} with TerritoryBro.com</div>
        </div>
      </div>
    </A5PrintFrame>
  );
};

export default RuralTerritoryCard;
