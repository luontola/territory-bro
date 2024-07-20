// Copyright © 2015-2024 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import Feature from "ol/Feature";
import {defaults as controlDefaults} from "ol/control";
import Attribution from "ol/control/Attribution";
import TileSource from "ol/source/Tile";
import OSM, {ATTRIBUTION as OSM_ATTRIBUTION} from "ol/source/OSM";
import View from "ol/View";
import XYZ from "ol/source/XYZ";
import WKT from "ol/format/WKT";
import TileLayer from "ol/layer/Tile";
import Stroke from "ol/style/Stroke";
import Fill from "ol/style/Fill";
import Text from "ol/style/Text";
import TileWMS from "ol/source/TileWMS";
import {defaults as interactionDefaults} from "ol/interaction";
import DragPan from "ol/interaction/DragPan";
import MouseWheelZoom from "ol/interaction/MouseWheelZoom";
import {platformModifierKeyOnly} from "ol/events/condition";
import {fromLonLat} from "ol/proj";
import ResetZoom from "./ResetZoom.ts";
import ShowMyLocation from "./ShowMyLocation.ts";
import {createXYZ} from "ol/tilegrid";
import i18n from "../i18n.ts";
import Geolocation from "ol/Geolocation";

export type LocationOnly = {
  location: string | null;
}
export type Territory = {
  number: string;
  location: string;
}
export type TerritoryPlus = Territory & {
  id: string;
  loaned?: boolean;
  staleness?: number;
}
export type MapRaster = {
  id: string;
  name: string;
  makeSource: () => TileSource;
};

const transition = 0;
export const mapRasters: MapRaster[] = [{
  id: 'osmhd',
  name: "World - OpenStreetMap (RRZE server, high DPI)",
  makeSource: () => new XYZ({
    url: 'https://{a-c}.osm.rrze.fau.de/osmhd/{z}/{x}/{y}.png',
    tileSize: [512, 512],
    tileGrid: createXYZ({tileSize: [256, 256]}),
    attributions: OSM_ATTRIBUTION,
    transition,
  })
}, {
  id: 'osm',
  name: "World - OpenStreetMap (official server, low DPI)",
  makeSource: () => new OSM({
    transition,
  })
}, {
  id: 'mmlTaustakartta',
  name: "Finland - Maanmittauslaitoksen taustakarttasarja",
  makeSource: () => new XYZ({
    url: 'https://tiles.kartat.kapsi.fi/taustakartta/{z}/{x}/{y}.jpg',
    tileSize: [256, 256],
    tileGrid: createXYZ({tileSize: [128, 128]}),
    attributions: '&copy; Maanmittauslaitos',
    transition,
  })
}, {
  id: 'vantaaKaupunkikartta',
  name: "Finland - Vantaan kaupunkikartta",
  makeSource: () => new TileWMS({
    url: 'https://gis.vantaa.fi/geoserver/wms',
    params: {'LAYERS': 'taustakartta:kaupunkikartta', 'TILED': true},
    serverType: 'geoserver',
    attributions: '&copy; Vantaan kaupunki',
    transition,
  })
}];

export function findMapRasterById(mapRasterId: string) {
  for (const mapRaster of mapRasters) {
    if (mapRaster.id === mapRasterId) {
      return mapRaster;
    }
  }
  return null;
}

export function wktToFeature(wkt: string): Feature {
  const feature = new WKT().readFeature(wkt);
  feature.getGeometry()!.transform('EPSG:4326', 'EPSG:3857');
  return feature;
}

export function wktToFeatures(wkt: string | null | undefined): Feature[] {
  if (wkt) {
    return [wktToFeature(wkt)];
  } else {
    return [];
  }
}

export function makeStreetsLayer() {
  return new TileLayer({
    source: mapRasters[0].makeSource()
  });
}

export function makeControls({resetZoom, startGeolocation}: {
  resetZoom: (map: any, opts: {}) => void,
  startGeolocation?: (map: any) => Geolocation
}) {
  const controls = controlDefaults({
    attribution: false,
    zoomOptions: {
      zoomInTipLabel: i18n.t('Map.zoomIn'),
      zoomOutTipLabel: i18n.t('Map.zoomOut'),
    }
  });
  if (startGeolocation) {
    controls.push(new ShowMyLocation(startGeolocation))
  }
  controls.push(new ResetZoom(resetZoom));
  controls.push(new Attribution({
    className: 'map-attribution',
    collapsible: false
  }));
  return controls;
}

export function makeInteractions() {
  return interactionDefaults({dragPan: false, mouseWheelZoom: false}).extend([
    new DragPan({
      condition: function (event) {
        if (event.originalEvent.pointerType === 'touch') {
          return (this as DragPan).getPointerCount() === 2 || platformModifierKeyOnly(event);
        }
        return true
      }
    }),
    new MouseWheelZoom({
      condition: platformModifierKeyOnly
    })
  ])
}

export function makePrintoutView() {
  return makeView({
    zoomFactor: 1.1, // zoom in small steps to enable fine tuning
  });
}

export function makeView(opts: {}) {
  return new View({
    center: fromLonLat([0.0, 0.0]),
    zoom: 1,
    minResolution: 0.1,
    ...opts,
  });
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
    stroke: new Stroke({color: 'rgba(255, 255, 255, 1.0)', width: 3.0}),
    overflow: true
  });
}
