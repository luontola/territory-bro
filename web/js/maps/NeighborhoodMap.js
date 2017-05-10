// Copyright © 2015-2017 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import React from "react";
import ol from "openlayers";
import type {MapRaster} from "./mapOptions";
import {
  makeControls,
  makeStreetsLayer,
  territoryFillStyle,
  territoryStrokeStyle,
  territoryTextStyle,
  wktToFeature
} from "./mapOptions";
import type {Territory} from "../api";
import OpenLayersMap from "./OpenLayersMap";

export default class NeighborhoodMap extends OpenLayersMap {
  props: {
    territory: Territory,
    mapRaster: MapRaster,
  };
  map: *;

  componentDidMount() {
    const {territory, mapRaster} = this.props;
    this.map = initNeighborhoodMap(this.element, territory);
    this.map.setStreetsLayerRaster(mapRaster);
  }

  componentDidUpdate() {
    const {mapRaster} = this.props;
    this.map.setStreetsLayerRaster(mapRaster);
  }
}

function initNeighborhoodMap(element: HTMLDivElement,
                             territory: Territory): * {
  const territoryNumber = territory.number;
  const territoryWkt = territory.location;

  const territoryLayer = new ol.layer.Vector({
    source: new ol.source.Vector({
      features: [wktToFeature(territoryWkt)]
    }),
    style: new ol.style.Style({
      stroke: territoryStrokeStyle(),
      fill: territoryFillStyle(),
      text: territoryTextStyle(territoryNumber, '180%')
    })
  });

  const streetsLayer = makeStreetsLayer();

  const map = new ol.Map({
    target: element,
    pixelRatio: 2, // render at high DPI for printing
    layers: [streetsLayer, territoryLayer],
    controls: makeControls(),
    view: new ol.View({
      center: ol.proj.fromLonLat([0.0, 0.0]),
      zoom: 1,
      minResolution: 1.25, // prevent zooming too close, show more surrounding for small territories
      zoomFactor: 1.1 // zoom in small steps to enable fine tuning
    })
  });
  map.getView().fit(
    territoryLayer.getSource().getExtent(),
    {
      padding: [5, 5, 5, 5],
      minResolution: 3.0
    }
  );

  return {
    setStreetsLayerRaster(mapRaster: MapRaster): void {
      streetsLayer.setSource(mapRaster.source);
    },
  }
}
