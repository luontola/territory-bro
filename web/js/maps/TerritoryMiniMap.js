// Copyright © 2015-2018 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import React from "react";
import {Map, View} from "ol";
import VectorLayer from "ol/layer/Vector";
import VectorSource from "ol/source/Vector"
import Style from "ol/style/Style";
import Stroke from "ol/style/Stroke";
import Fill from "ol/style/Fill";
import Icon from "ol/style/Icon";
import {fromLonLat} from "ol/proj";
import WKT from "ol/format/WKT";
import {makeStreetsLayer, wktToFeature} from "./mapOptions";
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
  const wkt = new WKT();
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
  if (!congregationWkt) {
    throw new Error(`The territory number ${territory.number} is not inside the congregation boundary. Make sure that a region has the congregation=t flag enabled.`);
  }
  if (!viewportWkt) {
    throw new Error(`The territory number ${territory.number} is not inside a minimap viewport. Make sure that a region has the minimap_viewport=t flag enabled. It can also be the same region as the congregation boundary.`);
  }

  const territoryLayer = new VectorLayer({
    source: new VectorSource({
      features: [wktToFeature(territoryWkt)]
    }),
    style: new Style({
      image: new Icon({
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

  const viewportSource = new VectorSource({
    features: [wktToFeature(viewportWkt)]
  });

  const congregationLayer = new VectorLayer({
    source: new VectorSource({
      features: [wktToFeature(congregationWkt)]
    }),
    style: new Style({
      stroke: new Stroke({
        color: 'rgba(0, 0, 0, 1.0)',
        width: 1.0
      })
    })
  });

  const subregionsLayer = new VectorLayer({
    source: new VectorSource({
      features: subregionsWkt ? [wktToFeature(subregionsWkt)] : []
    }),
    style: new Style({
      fill: new Fill({
        color: 'rgba(0, 0, 0, 0.3)'
      })
    })
  });

  const streetLayer = makeStreetsLayer();

  const map = new Map({
    target: element,
    pixelRatio: 2, // render at high DPI for printing
    layers: [streetLayer, subregionsLayer, congregationLayer, territoryLayer],
    controls: [],
    interactions: [],
    view: new View({
      center: fromLonLat([0.0, 0.0]),
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
