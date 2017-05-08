// Copyright © 2015-2017 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import React from "react";
import formatDate from "date-fns/format";
import {FormattedMessage} from "react-intl";
import TerritoryMap from "./TerritoryMap";
import TerritoryMiniMap from "./TerritoryMiniMap";
import type {Region, Territory} from "../api";
import type {MapRaster} from "../maps";
import CropMarks from "./CropMarks";
import styles from "./TerritoryCard.css";

const TerritoryCard = ({territory, regions, mapRaster}: {
  territory: Territory,
  regions: Array<Region>,
  mapRaster: MapRaster
}) => {
  const today = formatDate(new Date(), 'YYYY-MM-DD');
  return (
    <CropMarks className={styles.root}>

      <div className={styles.number}>{territory.number}</div>

      <div className={styles.minimap}>
        <TerritoryMiniMap territory={territory} regions={regions}/>
      </div>

      <div className={styles.title}>
        <FormattedMessage id="TerritoryCard.title"
                          defaultMessage="Territory Map Card"/>
      </div>

      <div className={styles.region}>{territory.region}</div>

      <div className={styles.map}>
        <TerritoryMap territory={territory} mapRaster={mapRaster}/>
      </div>

      <div className={styles.addresses}>{territory.address}</div>

      <div className={styles.disclaimer}>
        <div>Printed {today} with TerritoryBro.com</div>
      </div>

      <div className={styles.footer}>
        <FormattedMessage id="TerritoryCard.footer1"
                          defaultMessage="Please keep this card in the envelope. Do not soil, mark or bend it."/>
        <br/>
        <FormattedMessage id="TerritoryCard.footer2"
                          defaultMessage="Each time the territory is covered, please inform the brother who cares for the territory files."/>
      </div>

    </CropMarks>
  );
};

export default TerritoryCard;
