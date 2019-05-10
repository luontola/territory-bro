// Copyright © 2015-2019 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import React from "react";
import {Map, View} from "ol";
import VectorLayer from "ol/layer/Vector";
import VectorSource from "ol/source/Vector"
import MultiPolygon from 'ol/geom/MultiPolygon';
import Style from "ol/style/Style";
import Stroke from "ol/style/Stroke";
import Fill from "ol/style/Fill";
import Icon from "ol/style/Icon";
import {fromLonLat} from "ol/proj";
import WKT from "ol/format/WKT";
import {makeStreetsLayer, wktToFeature} from "./mapOptions";
import type {Territory} from "../api";
import OpenLayersMap from "./OpenLayersMap";

type Props = {
  territory: Territory,
  congregation: any,
};

export default class TerritoryMiniMap extends OpenLayersMap<Props> {
  componentDidMount() {
    const {territory, congregation} = this.props;
    initTerritoryMiniMap({
      element: this.element,
      congregationWkt: getCongregationBoundaryWkt(congregation),
      viewportWkt: getMinimapViewportWkt(congregation, territory),
      subregionWkt: getSubregionWkt(congregation, territory),
      territoryCenterWkt: getTerritoryCenterWkt(territory)
    });
  }
}

function mergeMultiPolygons(multiPolygons) {
  const wkt = new WKT();
  const merged = multiPolygons
    .map(p => wkt.readFeature(p).getGeometry())
    .reduce((a, b) => new MultiPolygon([...a.getPolygons(), ...b.getPolygons()]));
  return wkt.writeGeometry(merged)
}

export function getCongregationBoundaryWkt(congregation) {
  if (congregation.congregationBoundaries.length === 0) {
    throw new Error("Congregation boundaries not defined");
  }
  return mergeMultiPolygons(congregation.congregationBoundaries.map(boundary => boundary.location));
}

export function getMinimapViewportWkt(congregation, territory) {
  const wkt = new WKT();
  const centerPoint = wkt.readFeature(territory.location).getGeometry().getInteriorPoints().getPoint(0);
  const territoryCoordinate = centerPoint.getCoordinates();

  let viewportWkt = '';
  congregation.cardMinimapViewports.forEach(viewport => {
    if (wkt.readFeature(viewport.location).getGeometry().intersectsCoordinate(territoryCoordinate)) {
      viewportWkt = viewport.location;
    }
  });
  return viewportWkt;
}

export function getSubregionWkt(congregation, territory) {
  const wkt = new WKT();
  const centerPoint = wkt.readFeature(territory.location).getGeometry().getInteriorPoints().getPoint(0);
  const territoryCoordinate = centerPoint.getCoordinates();

  let subregionWkt = '';
  congregation.subregions.forEach(subregion => {
    if (wkt.readFeature(subregion.location).getGeometry().intersectsCoordinate(territoryCoordinate)) {
      subregionWkt = subregion.location;
    }
  });
  return subregionWkt;
}

function getTerritoryCenterWkt(territory) {
  const wkt = new WKT();
  const centerPoint = wkt.readFeature(territory.location).getGeometry().getInteriorPoints().getPoint(0);
  return wkt.writeGeometry(centerPoint);
}

function initTerritoryMiniMap({element, congregationWkt, viewportWkt, subregionWkt, territoryCenterWkt}) {
  if (!viewportWkt) {
    viewportWkt = congregationWkt;
  }

  const territoryLayer = new VectorLayer({
    source: new VectorSource({
      features: [wktToFeature(territoryCenterWkt)]
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
      features: subregionWkt ? [wktToFeature(subregionWkt)] : []
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
