// Copyright © 2015-2017 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import ol from "openlayers";
import zipObject from "lodash-es/zipObject";

export type MapRaster = {
  id: string,
  name: string,
  source: ol.source.Source,
}
export const mapRasters: Array<MapRaster> = [
  {
    id: 'osm',
    name: "World - OpenStreetMap",
    source: new ol.source.OSM()
  },
  {
    id: 'osmHighDpi',
    name: "World - OpenStreetMap (High DPI)",
    source: new ol.source.XYZ({
      url: '//a.osm.rrze.fau.de/osmhd/{z}/{x}/{y}.png',
      tileSize: [256, 256],
      tilePixelRatio: 2,
      attributions: [
        ol.source.OSM.ATTRIBUTION
      ]
    })
  },
  {
    id: 'mmlTaustakartta',
    name: "Finland - Maanmittauslaitos taustakarttasarja",
    source: new ol.source.XYZ({
      url: '//tiles.kartat.kapsi.fi/taustakartta/{z}/{x}/{y}.jpg',
      tileSize: [256, 256],
      attributions: [
        new ol.Attribution({
          html: '&copy; Maanmittauslaitos'
        })
      ]
    })
  },
  {
    id: 'mmlTaustakarttaHighDpi',
    name: "Finland - Maanmittauslaitos taustakarttasarja (High DPI)",
    source: new ol.source.XYZ({
      url: '//tiles.kartat.kapsi.fi/taustakartta/{z}/{x}/{y}.jpg',
      tileSize: [128, 128],
      tilePixelRatio: 2,
      attributions: [
        new ol.Attribution({
          html: '&copy; Maanmittauslaitos'
        })
      ]
    })
  },
];

const mapRastersById = zipObject(mapRasters.map(m => m.id), mapRasters);
export const defaultMapRaster: MapRaster = mapRastersById.osmHighDpi;

export function wktToFeature(wkt: string): ol.Feature {
  const feature = new ol.format.WKT().readFeature(wkt);
  feature.getGeometry().transform('EPSG:4326', 'EPSG:3857');
  return feature;
}

export function makeStreetsLayer() {
  return new ol.layer.Tile({
    source: defaultMapRaster.source
  });
}

export function makeControls() {
  const attribution = new ol.control.Attribution({
    className: 'map-attribution',
    collapsible: false
  });
  return ol.control.defaults({attribution: false}).extend([attribution]);
}

// visual style

export function territoryStrokeStyle() {
  return new ol.style.Stroke({
    color: 'rgba(255, 0, 0, 0.6)',
    width: 2.0
  });
}

export function territoryFillStyle() {
  return new ol.style.Fill({
    color: 'rgba(255, 0, 0, 0.1)'
  });
}

export function territoryTextStyle(territoryNumber: string, fontSize: string) {
  return new ol.style.Text({
    text: territoryNumber,
    font: 'bold ' + fontSize + ' sans-serif',
    fill: new ol.style.Fill({color: 'rgba(0, 0, 0, 1.0)'}),
    stroke: new ol.style.Stroke({color: 'rgba(255, 255, 255, 1.0)', width: 3.0})
  });
}
