// Copyright © 2015-2017 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import "../../css/territory-cards.css";
import React from "react";
import {Layout} from "./Layout";
import formatDate from "date-fns/format";
import i18n from "../i18n";
import {initTerritoryMap, initTerritoryMiniMap} from "../maps";

class TerritoryCard extends React.Component {
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

          <div className="title">{ i18n.en['territory-card.title'] }</div>
          <div className="region">{territory.region}</div>
          <div className="map" ref={el => this.map = el}/>
          <div className="addresses">{territory.address}</div>

          <div className="disclaimer">
            <div>Printed {today} with TerritoryBro.com</div>
          </div>

          <div className="footer">{i18n.en['territory-card.footer1']}
            <br/>{i18n.en['territory-card.footer2']}</div>
        </div>
        <div className="crop-mark-bottom-left"><img src="/img/crop-mark.svg" alt=""/></div>
        <div className="crop-mark-bottom-right"><img src="/img/crop-mark.svg" alt=""/></div>
      </div>
    );
  }
}

const TerritoryCardsPage = ({territories, regions}) => (
  <Layout>
    <h1 className="no-print">Territory Cards</h1>
    {territories.map(territory =>
      <TerritoryCard key={territory.id} territory={territory} regions={regions}/>
    )}
  </Layout>
);

export {TerritoryCardsPage};
