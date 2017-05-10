// Copyright © 2015-2017 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import React from "react";
import ol from "openlayers";
import {makeStreetsLayer, wktToFeature} from "../maps";
import type {Region, Territory} from "../api";
import OpenLayersMap from "./OpenLayersMap";

export default class TerritoryMiniMap extends OpenLayersMap {
  props: {
    territory: Territory,
    regions: Array<Region>,
  };

  componentDidMount() {
    const {territory, regions} = this.props;
    initTerritoryMiniMap(this.element, territory, regions);
  }
}

function initTerritoryMiniMap(element: HTMLDivElement,
                              territory: Territory,
                              regions: Array<Region>): void {
  const wkt = new ol.format.WKT();
  const centerPoint = wkt.readFeature(territory.location).getGeometry().getInteriorPoint();
  const territoryWkt = wkt.writeGeometry(centerPoint);
  // TODO: handle it gracefully if one of the following is not initialized
  let viewportWkt = '';
  let congregationWkt = '';
  let subregionsWkt = '';
  const territoryCoordinate = centerPoint.getCoordinates();
  regions.forEach(region => {
    const regionGeom = wkt.readFeature(region.location).getGeometry();
    if (regionGeom.intersectsCoordinate(territoryCoordinate)) {
      if (region.minimap_viewport) {
        viewportWkt = region.location;
      }
      if (region.congregation) {
        congregationWkt = region.location;
      }
      if (region.subregion) {
        // TODO: support multiple (nested) subregions
        subregionsWkt = region.location;
      }
    }
  });

  const territoryLayer = new ol.layer.Vector({
    source: new ol.source.Vector({
      features: [wktToFeature(territoryWkt)]
    }),
    style: new ol.style.Style({
      image: new ol.style.Icon({
        src: '/img/minimap-territory.svg',
        imgSize: [10, 10],
        snapToPixel: true,
      }),
      // XXX: Circle does not yet support HiDPI, so we need to use SVG instead. See https://github.com/openlayers/openlayers/issues/1574
      // image: new ol.style.Circle({
      //   radius: 3.5,
      //   fill: new ol.style.Fill({
      //     color: 'rgba(0, 0, 0, 1.0)'
      //   }),
      //   snapToPixel: true,
      // })
    })
  });

  const viewportSource = new ol.source.Vector({
    features: [wktToFeature(viewportWkt)]
  });

  const congregationLayer = new ol.layer.Vector({
    source: new ol.source.Vector({
      features: [wktToFeature(congregationWkt)]
    }),
    style: new ol.style.Style({
      stroke: new ol.style.Stroke({
        color: 'rgba(0, 0, 0, 1.0)',
        width: 1.0
      })
    })
  });

  const subregionsLayer = new ol.layer.Vector({
    source: new ol.source.Vector({
      features: [wktToFeature(subregionsWkt)]
    }),
    style: new ol.style.Style({
      fill: new ol.style.Fill({
        color: 'rgba(0, 0, 0, 0.3)'
      })
    })
  });

  const streetLayer = makeStreetsLayer();

  const map = new ol.Map({
    target: element,
    pixelRatio: 2, // render at high DPI for printing
    layers: [streetLayer, subregionsLayer, congregationLayer, territoryLayer],
    controls: [],
    interactions: [],
    view: new ol.View({
      center: ol.proj.fromLonLat([0.0, 0.0]),
      zoom: 1
    })
  });
  map.getView().fit(
    viewportSource.getExtent(),
    {
      padding: [1, 1, 1, 1], // minimum padding where the congregation lines still show up
      constrainResolution: false
    }
  );
}
