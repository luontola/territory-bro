// Copyright © 2015-2018 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import {Feature} from "ol";
import {Attribution, defaults as controlDefaults} from "ol/control"
import Source from "ol/source/Source";
import OSM, {ATTRIBUTION as OSM_ATTRIBUTION} from "ol/source/OSM";
import XYZ from "ol/source/XYZ";
import WKT from "ol/format/WKT";
import Tile from "ol/layer/Tile";
import Stroke from "ol/style/Stroke";
import Fill from "ol/style/Fill";
import Text from "ol/style/Text";

export type MapRaster = {
  id: string,
  name: string,
  source: Source,
}
export const mapRasters: Array<MapRaster> = [
  {
    id: 'osm',
    name: "World - OpenStreetMap",
    source: new OSM()
  },
  {
    id: 'osmHighDpi',
    name: "World - OpenStreetMap (High DPI)",
    source: new XYZ({
      url: '//a.osm.rrze.fau.de/osmhd/{z}/{x}/{y}.png',
      tileSize: [256, 256],
      tilePixelRatio: 2,
      attributions: OSM_ATTRIBUTION
    })
  },
  {
    id: 'mmlTaustakartta',
    name: "Finland - Maanmittauslaitos taustakarttasarja",
    source: new XYZ({
      url: '//tiles.kartat.kapsi.fi/taustakartta/{z}/{x}/{y}.jpg',
      tileSize: [256, 256],
      attributions: '&copy; Maanmittauslaitos'
    })
  },
  {
    id: 'mmlTaustakarttaHighDpi',
    name: "Finland - Maanmittauslaitos taustakarttasarja (High DPI)",
    source: new XYZ({
      url: '//tiles.kartat.kapsi.fi/taustakartta/{z}/{x}/{y}.jpg',
      tileSize: [128, 128],
      tilePixelRatio: 2,
      attributions: '&copy; Maanmittauslaitos'
    })
  },
];

export function wktToFeature(wkt: string): Feature {
  const feature = new WKT().readFeature(wkt);
  feature.getGeometry().transform('EPSG:4326', 'EPSG:3857');
  return feature;
}

export function makeStreetsLayer() {
  return new Tile({
    source: new OSM()
  });
}

export function makeControls() {
  const attribution = new Attribution({
    className: 'map-attribution',
    collapsible: false
  });
  return controlDefaults({attribution: false}).extend([attribution]);
}

// visual style

export function territoryStrokeStyle() {
  return new Stroke({
    color: 'rgba(255, 0, 0, 0.6)',
    width: 2.0
  });
}

export function territoryFillStyle() {
  return new Fill({
    color: 'rgba(255, 0, 0, 0.1)'
  });
}

export function territoryTextStyle(territoryNumber: string, fontSize: string) {
  return new Text({
    text: territoryNumber,
    font: 'bold ' + fontSize + ' sans-serif',
    fill: new Fill({color: 'rgba(0, 0, 0, 1.0)'}),
    stroke: new Stroke({color: 'rgba(255, 255, 255, 1.0)', width: 3.0})
  });
}
