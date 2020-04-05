// Copyright © 2015-2019 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import React from "react";
import {FormattedMessage} from "react-intl";
import TerritoryMap from "../maps/TerritoryMap";
import TerritoryMiniMap from "../maps/TerritoryMiniMap";
import {getCongregationById} from "../api";
import CropMarks from "./CropMarks";
import styles from "./TerritoryCard.css";
import PrintDateNotice from "./PrintDateNotice";

const TerritoryCard = ({
                         territory,
                         territoryId,
                         congregation,
                         congregationId,
                         mapRaster
                       }) => {
  congregation = congregation || getCongregationById(congregationId);
  territory = territory || congregation.getTerritoryById(territoryId);
  return <CropMarks>
    <div className={styles.root}>

      <div className={styles.minimap}>
        <TerritoryMiniMap territory={territory} congregation={congregation}/>
      </div>

      <div className={styles.header}>
        <div className={styles.title}>
          <FormattedMessage id="TerritoryCard.title" defaultMessage="Territory Map Card"/>
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

      <div className={styles.addresses}>{territory.addresses}</div>

      <div className={styles.footer}>
        <FormattedMessage id="TerritoryCard.footer"
                          defaultMessage={"Please keep this card in the envelope. Do not soil, mark or bend it. \n Each time the territory is covered, please inform the brother who cares for the territory files."}/>
      </div>

    </div>
  </CropMarks>;
};

export default TerritoryCard;
