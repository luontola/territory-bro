// Copyright © 2015-2017 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import "../../css/territory-cards.css";
import React from "react";
import {Layout} from "./Layout";

let NeighborhoodMapsPage = () => (
  <Layout>
    <h1 className="no-print">Neighborhood Maps</h1>

    <div className="croppable-territory-card">
      <div className="crop-mark-top-left"><img src="/img/crop-mark.svg" alt=""/></div>
      <div className="crop-mark-top-right"><img src="/img/crop-mark.svg" alt=""/></div>
      <div id="neighborhood-map-{{ territory.id }}" className="crop-area neighborhood-map"/>
      <div className="crop-mark-bottom-left"><img src="/img/crop-mark.svg" alt=""/></div>
      <div className="crop-mark-bottom-right"><img src="/img/crop-mark.svg" alt=""/></div>
    </div>
  </Layout>
);

export {NeighborhoodMapsPage};
