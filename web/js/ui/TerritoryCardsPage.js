// Copyright © 2015-2017 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import "../../css/territory-cards.css";
import React from "react";
import {Layout} from "./Layout";
import formatDate from "date-fns/format";
import {initTerritoryMap, initTerritoryMiniMap} from "../maps";
import type {Region, Territory} from "../api";
import {FormattedMessage} from "react-intl";

class TerritoryCard extends React.Component {
  map: HTMLDivElement;
  minimap: HTMLDivElement;

  componentDidMount() {
    const {territory, regions} = this.props;
    initTerritoryMap(this.map, territory);
    initTerritoryMiniMap(this.minimap, territory, regions);
  }

  render() {
    const {territory} = this.props;
    const today = formatDate(new Date(), 'YYYY-MM-DD');
    return (
      <div className="croppable-territory-card">
        <div className="crop-mark-top-left"><img src="/img/crop-mark.svg" alt=""/></div>
        <div className="crop-mark-top-right"><img src="/img/crop-mark.svg" alt=""/></div>
        <div className="crop-area territory-card">
          <div className="number">{territory.number}</div>
          <div className="minimap" ref={el => this.minimap = el}/>

          <div className="title">
            <FormattedMessage id="TerritoryCard.title"
                              defaultMessage="Territory Map Card"/>
          </div>
          <div className="region">{territory.region}</div>
          <div className="map" ref={el => this.map = el}/>
          <div className="addresses">{territory.address}</div>

          <div className="disclaimer">
            <div>Printed {today} with TerritoryBro.com</div>
          </div>

          <div className="footer">
            <FormattedMessage id="TerritoryCard.footer1"
                              defaultMessage="Please keep this card in the envelope. Do not soil, mark or bend it."/>
            <br/>
            <FormattedMessage id="TerritoryCard.footer2"
                              defaultMessage="Each time the territory is covered, please inform the brother who cares for the territory files"/>
          </div>
        </div>
        <div className="crop-mark-bottom-left"><img src="/img/crop-mark.svg" alt=""/></div>
        <div className="crop-mark-bottom-right"><img src="/img/crop-mark.svg" alt=""/></div>
      </div>
    );
  }
}

const TerritoryCardsPage = ({territories, regions}: {
  territories: Array<Territory>,
  regions: Array<Region>
}) => (
  <Layout>
    <h1 className="no-print">Territory Cards</h1>
    {territories.map(territory =>
      <TerritoryCard key={territory.id} territory={territory} regions={regions}/>
    )}
  </Layout>
);

export {TerritoryCardsPage};
