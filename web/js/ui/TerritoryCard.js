// Copyright © 2015-2017 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import "../../css/territory-cards.css";
import React from "react";
import formatDate from "date-fns/format";
import {FormattedMessage} from "react-intl";
import TerritoryMap from "./TerritoryMap";
import TerritoryMiniMap from "./TerritoryMiniMap";
import type {Region, Territory} from "../api";
import type {MapRaster} from "../maps";

const TerritoryCard = ({territory, regions, mapRaster}: {
  territory: Territory,
  regions: Array<Region>,
  mapRaster: MapRaster
}) => {
  const today = formatDate(new Date(), 'YYYY-MM-DD');
  return (
    <div className="croppable-territory-card">
      <div className="crop-mark-top-left"><img src="/img/crop-mark.svg" alt=""/></div>
      <div className="crop-mark-top-right"><img src="/img/crop-mark.svg" alt=""/></div>
      <div className="crop-area territory-card">
        <div className="number">{territory.number}</div>
        <TerritoryMiniMap className="minimap" territory={territory} regions={regions}/>

        <div className="title">
          <FormattedMessage id="TerritoryCard.title"
                            defaultMessage="Territory Map Card"/>
        </div>
        <div className="region">{territory.region}</div>
        <TerritoryMap className="map" territory={territory} mapRaster={mapRaster}/>
        <div className="addresses">{territory.address}</div>

        <div className="disclaimer">
          <div>Printed {today} with TerritoryBro.com</div>
        </div>

        <div className="footer">
          <FormattedMessage id="TerritoryCard.footer1"
                            defaultMessage="Please keep this card in the envelope. Do not soil, mark or bend it."/>
          <br/>
          <FormattedMessage id="TerritoryCard.footer2"
                            defaultMessage="Each time the territory is covered, please inform the brother who cares for the territory files."/>
        </div>
      </div>
      <div className="crop-mark-bottom-left"><img src="/img/crop-mark.svg" alt=""/></div>
      <div className="crop-mark-bottom-right"><img src="/img/crop-mark.svg" alt=""/></div>
    </div>
  );
};

export default TerritoryCard;
