// Copyright © 2015-2017 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import "../../css/territory-cards.css";
import React from "react";
import {Layout} from "./Layout";

let RegionMapsPage = () => (
  <Layout>
    <h1 className="no-print">Region Maps</h1>

    <div className="region-page crop-area">
      <div className="name">{ 'region.name' }</div>
      <div id="region-map-{{ region.id }}" className="region-map"/>
    </div>
  </Layout>
);

export {RegionMapsPage};
