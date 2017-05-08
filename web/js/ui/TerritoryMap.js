// Copyright © 2015-2017 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import React from "react";
import type {MapRaster} from "../maps";
import {initTerritoryMap} from "../maps";
import type {Territory} from "../api";
import OpenLayersMap from "./OpenLayersMap";

export default class TerritoryMap extends OpenLayersMap {
  props: {
    territory: Territory,
    mapRaster: MapRaster,
    className?: string,
  };
  map: *;

  componentDidMount() {
    const {territory, mapRaster} = this.props;
    this.map = initTerritoryMap(this.element, territory);
    this.map.setStreetsLayerRaster(mapRaster);
  }

  componentDidUpdate() {
    const {mapRaster} = this.props;
    this.map.setStreetsLayerRaster(mapRaster);
  }
}
