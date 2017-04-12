// Copyright © 2015-2017 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import "../../css/territory-cards.css";
import React from "react";
import {Layout} from "./Layout";

let TerritoryCardsPage = () => (
  <Layout>
    <h1 className="no-print">Territory Cards</h1>

    <div className="croppable-territory-card">
      <div className="crop-mark-top-left"><img src="/img/crop-mark.svg" alt=""/></div>
      <div className="crop-mark-top-right"><img src="/img/crop-mark.svg" alt=""/></div>
      <div className="crop-area territory-card">
        <div className="number">{ 'territory.number' }</div>
        <div id="minimap-{{ territory.id }}" className="minimap"/>

        <div className="title">{ 'i18n.territory-card.title' }</div>
        <div className="region">{ 'territory.region' }</div>

        <div id="map-{{ territory.id }}" className="map"/>
        <div className="addresses">{ 'territory.address|semicolon-to-newline' }</div>

        <div className="disclaimer">
          <div>Printed { 'today' } with TerritoryBro.com</div>
        </div>

        <div className="footer">{ 'i18n.territory-card.footer1' }<br/>{ 'i18n.territory-card.footer2' }</div>
      </div>
      <div className="crop-mark-bottom-left"><img src="/img/crop-mark.svg" alt=""/></div>
      <div className="crop-mark-bottom-right"><img src="/img/crop-mark.svg" alt=""/></div>
    </div>
  </Layout>
);

export {TerritoryCardsPage};
