// Copyright © 2015-2017 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import "../../css/territory-cards.css";
import React from "react";
import {Layout} from "./Layout";
import moment from "moment";
import i18n from "../i18n";
import {initTerritoryMap} from "../maps";

class TerritoryCard extends React.Component {
  constructor(props) {
    super(props);
    this.today = moment().format('YYYY-MM-DD');
  }

  componentDidMount() {
    const territory = this.props.territory;
    initTerritoryMap(this.map, territory.location);
    //initTerritoryMiniMap(this.minimap, territory.center, territory.minimap_viewport, territory.congregation, territory.subregions);
  }

  render() {
    const territory = this.props.territory;
    const today = this.today;
    return (
      <div key={territory.id} className="croppable-territory-card">
        <div className="crop-mark-top-left"><img src="/img/crop-mark.svg" alt=""/></div>
        <div className="crop-mark-top-right"><img src="/img/crop-mark.svg" alt=""/></div>
        <div className="crop-area territory-card">
          <div className="number">{territory.number}</div>
          <div id={`minimap-${territory.id}`} className="minimap" ref={el => this.minimap = el}/>

          <div className="title">{ i18n.en['territory-card.title'] }</div>
          <div className="region">{territory.region}</div>
          <div id={`map-${territory.id}`} className="map" ref={el => this.map = el}/>
          <div className="addresses">{territory.address.replace(';', '\n')}</div>

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

let TerritoryCardsPage = ({territories, regions}) => (
  <Layout>
    <h1 className="no-print">Territory Cards</h1>
    {territories.map(territory =>
      <TerritoryCard territory={territory} key={territory.id}/>
    )}
  </Layout>
);

export {TerritoryCardsPage};
