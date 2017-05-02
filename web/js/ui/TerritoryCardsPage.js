// Copyright © 2015-2017 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import "../../css/territory-cards.css";
import React from "react";
import {Layout} from "./Layout";
import formatDate from "date-fns/format";
import type {MapRaster} from "../maps";
import {initTerritoryMap, initTerritoryMiniMap} from "../maps";
import type {Region, Territory} from "../api";
import {FormattedMessage} from "react-intl";
import PrintOptionsForm, {getMapRaster} from "./PrintOptionsForm";
import {connect} from "react-redux";
import ol from "openlayers";

class TerritoryCard extends React.Component {
  mapElement: HTMLDivElement;
  minimapElement: HTMLDivElement;
  map: ol.Map;

  componentDidMount() {
    const {territory, regions, mapRaster} = this.props;
    this.map = initTerritoryMap(this.mapElement, territory);
    initTerritoryMiniMap(this.minimapElement, territory, regions);
    this.setStreetLayerRaster(mapRaster);
  }

  componentDidUpdate() {
    const {mapRaster} = this.props;
    this.setStreetLayerRaster(mapRaster);
  }

  setStreetLayerRaster(mapRaster) {
    // TODO: spike code, extract a nicer facade for customizing the map
    const streetLayerIndex = 0;
    this.map.getLayers().setAt(streetLayerIndex, new ol.layer.Tile({
      source: mapRaster.source
    }));
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
          <div className="minimap" ref={el => this.minimapElement = el}/>

          <div className="title">
            <FormattedMessage id="TerritoryCard.title"
                              defaultMessage="Territory Map Card"/>
          </div>
          <div className="region">{territory.region}</div>
          <div className="map" ref={el => this.mapElement = el}/>
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
  }
}

let TerritoryCardsPage = ({territories, regions, mapRaster}: {
  territories: Array<Territory>,
  regions: Array<Region>,
  mapRaster: MapRaster,
}) => (
  <Layout>
    <div className="no-print">
      <h1>Territory Cards</h1>
      <PrintOptionsForm/>
    </div>
    {territories.map(territory =>
      <TerritoryCard key={territory.id} territory={territory} regions={regions} mapRaster={mapRaster}/>
    )}
  </Layout>
);

function mapStateToProps(state) {
  return {
    mapRaster: getMapRaster(state),
  };
}

TerritoryCardsPage = connect(mapStateToProps)(TerritoryCardsPage);

export {TerritoryCardsPage};
