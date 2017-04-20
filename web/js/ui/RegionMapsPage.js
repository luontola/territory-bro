// Copyright © 2015-2017 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import "../../css/territory-cards.css";
import React from "react";
import {Layout} from "./Layout";
import {initRegionMap} from "../maps";
import type {Region, Territory} from "../api";

class RegionMap extends React.Component {
  map: HTMLDivElement;

  componentDidMount() {
    const {region, territories} = this.props;
    initRegionMap(this.map, region, territories);
  }

  render() {
    const {region} = this.props;
    return (
      <div className="region-page crop-area">
        <div className="name">{region.name}</div>
        <div className="region-map" ref={el => this.map = el}/>
      </div>
    );
  }
}

const RegionMapsPage = ({territories, regions}: {
  territories: Array<Territory>,
  regions: Array<Region>
}) => (
  <Layout>
    <h1 className="no-print">Region Maps</h1>
    {regions
      .filter(region => region.congregation || region.subregion)
      .map(region =>
        <RegionMap key={region.id} region={region} territories={territories}/>
      )}
  </Layout>
);

export {RegionMapsPage};
