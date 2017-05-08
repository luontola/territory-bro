// Copyright © 2015-2017 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import React from "react";
import type {MapRaster} from "../maps";
import {initRegionMap} from "../maps";
import type {Region, Territory} from "../api";
import OpenLayersMap from "./OpenLayersMap";

export default class RegionMap extends OpenLayersMap {
  props: {
    region: Region,
    territories: Array<Territory>,
    mapRaster: MapRaster,
  };
  map: *;

  componentDidMount() {
    const {region, territories, mapRaster} = this.props;
    this.map = initRegionMap(this.element, region, territories);
    this.map.setStreetsLayerRaster(mapRaster);
  }

  componentDidUpdate() {
    const {mapRaster} = this.props;
    this.map.setStreetsLayerRaster(mapRaster);
  }
}
