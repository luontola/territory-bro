// Copyright © 2015-2017 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import React from "react";
import ol from "openlayers";
import type {MapRaster} from "./mapOptions";
import {makeControls, makeStreetsLayer, territoryStrokeStyle, territoryTextStyle, wktToFeature} from "./mapOptions";
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

function initRegionMap(element: HTMLDivElement,
                       region: Region,
                       territories: Array<Territory>): * {
  const regionLayer = new ol.layer.Vector({
    source: new ol.source.Vector({
      features: [wktToFeature(region.location)]
    }),
    style: new ol.style.Style({
      stroke: new ol.style.Stroke({
        color: 'rgba(0, 0, 0, 0.6)',
        width: 4.0
      })
    })
  });

  const territoryLayer = new ol.layer.Vector({
    source: new ol.source.Vector({
      features: territories.map(function (territory) {
        const feature = wktToFeature(territory.location);
        feature.set('number', territory.number);
        return feature;
      })
    }),
    style: function (feature, resolution) {
      const style = new ol.style.Style({
        stroke: territoryStrokeStyle(),
        text: territoryTextStyle(feature.get('number'), '5mm')
      });
      return [style];
    }
  });

  const streetsLayer = makeStreetsLayer();

  const map = new ol.Map({
    target: element,
    pixelRatio: 2, // render at high DPI for printing
    layers: [streetsLayer, regionLayer, territoryLayer],
    controls: makeControls(),
    view: new ol.View({
      center: ol.proj.fromLonLat([0.0, 0.0]),
      zoom: 1,
      minResolution: 1.25, // prevent zooming too close, show more surrounding for small territories
      zoomFactor: 1.1 // zoom in small steps to enable fine tuning
    })
  });
  map.getView().fit(
    regionLayer.getSource().getExtent(),
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
