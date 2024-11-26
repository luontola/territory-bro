import Feature, {FeatureLike} from "ol/Feature";
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
import {fromLonLat, Projection} from "ol/proj";
import ResetZoom from "./ResetZoom.ts";
import ShowMyLocation from "./ShowMyLocation.ts";
import i18n from "../i18n.ts";
import Geolocation from "ol/Geolocation";
import proj4 from 'proj4';
import {register} from 'ol/proj/proj4';
import Map from "ol/Map";
import {getHeight as getExtentHeight, getWidth as getExtentWidth} from "ol/extent";

proj4.defs(
  'EPSG:3067',
  '+proj=utm +zone=35 +ellps=GRS80 +towgs84=0,0,0,0,0,0,0 +units=m +no_defs +type=crs',
);
register(proj4);

const WEB_MERCATOR = 'EPSG:3857'; // OpenLayers default projection
const WGS84 = 'EPSG:4326'; // used in GPS
const EPSG3067 = new Projection({
  code: 'EPSG:3067',
  extent: [-548576, 6291456, 1548576, 8388608],
  units: 'm'
});

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
    tileSize: [256, 256],
    tilePixelRatio: 2,
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
    url: 'https://tiles.kartat.kapsi.fi/taustakartta/{z}/{x}/{y}.png',
    tileSize: [128, 128],
    tilePixelRatio: 2,
    attributions: '&copy; Maanmittauslaitos',
    transition,
  })
}, {
  id: 'mmlTaustakartta3067',
  name: "Finland - Maanmittauslaitoksen taustakarttasarja (backup)",
  makeSource: () => new XYZ({
    url: 'https://tiles.kartat.kapsi.fi/taustakartta_3067/{z}/{x}/{y}.jpg',
    projection: EPSG3067,
    tileSize: [128, 128],
    tilePixelRatio: 2,
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

export const assignedBorderColor = 'rgba(255, 0, 0, 0.6)';
export const assignedBackgroundColors = {
  0: 'rgba(255, 255, 255, 0.1)',
  1: 'rgba(255, 248, 230, 0.2)',
  2: 'rgba(255, 241, 205, 0.3)',
  3: 'rgba(255, 233, 178, 0.3)',
  4: 'rgba(255, 225, 151, 0.4)',
  5: 'rgba(255, 214, 110, 0.4)',
  6: 'rgba(255, 203, 54, 0.4)',
  7: 'rgba(255, 182, 50, 0.4)',
  8: 'rgba(255, 161, 45, 0.4)',
  9: 'rgba(255, 139, 40, 0.4)',
  10: 'rgba(255, 115, 36, 0.4)',
  11: 'rgba(255, 77, 30, 0.4)',
  12: 'rgba(255, 0, 23, 0.5)',
  // OpenLayers doesn't support color-mix. To tweak the colors, uncomment the following
  // original colors and copy their RBG values from the legend using Digital Colour Meter.app
  // 0: 'color-mix(in oklab, #ffffff, #ffcc00 0%)',
  // 1: 'color-mix(in oklab, #ffffff, #ffcc00 15%)',
  // 2: 'color-mix(in oklab, #ffffff, #ffcc00 30%)',
  // 3: 'color-mix(in oklab, #ffffff, #ffcc00 45%)',
  // 4: 'color-mix(in oklab, #ffffff, #ffcc00 60%)',
  // 5: 'color-mix(in oklab, #ffffff, #ffcc00 80%)',
  // 6: 'color-mix(in oklab, #ffffff, #ffcc00 100%)',
  // 7: 'color-mix(in oklab, #ffcc00, #ff0000 15%)',
  // 8: 'color-mix(in oklab, #ffcc00, #ff0000 30%)',
  // 9: 'color-mix(in oklab, #ffcc00, #ff0000 45%)',
  // 10: 'color-mix(in oklab, #ffcc00, #ff0000 60%)',
  // 11: 'color-mix(in oklab, #ffcc00, #ff0000 80%)',
  // 12: 'color-mix(in oklab, #ffcc00, #ff0000 100%)',
}
export const vacantBorderColor = 'rgba(0, 0, 255, 0.6)'
export const vacantBackgroundColors = {
  0: 'rgba(255, 255, 255, 0.1)',
  1: 'rgba(226, 248, 255, 0.2)',
  2: 'rgba(196, 241, 255, 0.3)',
  3: 'rgba(164, 234, 255, 0.3)',
  4: 'rgba(126, 227, 255, 0.4)',
  5: 'rgba(51, 216, 254, 0.4)',
  6: 'rgba(0, 205, 252, 0.4)',
  7: 'rgba(0, 185, 253, 0.4)',
  8: 'rgba(0, 165, 254, 0.4)',
  9: 'rgba(0, 143, 254, 0.4)',
  10: 'rgba(0, 120, 253, 0.4)',
  11: 'rgba(0, 56, 251, 0.4)',
  12: 'rgba(0, 33, 249, 0.5)',
  // OpenLayers doesn't support color-mix. To tweak the colors, uncomment the following
  // original colors and copy their RBG values from the legend using Digital Colour Meter.app
  // 0: 'color-mix(in oklab, #ffffff, #00ccff 0%)',
  // 1: 'color-mix(in oklab, #ffffff, #00ccff 15%)',
  // 2: 'color-mix(in oklab, #ffffff, #00ccff 30%)',
  // 3: 'color-mix(in oklab, #ffffff, #00ccff 45%)',
  // 4: 'color-mix(in oklab, #ffffff, #00ccff 60%)',
  // 5: 'color-mix(in oklab, #ffffff, #00ccff 80%)',
  // 6: 'color-mix(in oklab, #ffffff, #00ccff 100%)',
  // 7: 'color-mix(in oklab, #00ccff, #0000ff 15%)',
  // 8: 'color-mix(in oklab, #00ccff, #0000ff 30%)',
  // 9: 'color-mix(in oklab, #00ccff, #0000ff 45%)',
  // 10: 'color-mix(in oklab, #00ccff, #0000ff 60%)',
  // 11: 'color-mix(in oklab, #00ccff, #0000ff 80%)',
  // 12: 'color-mix(in oklab, #00ccff, #0000ff 100%)',
}

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
  feature.getGeometry()!.transform(WGS84, WEB_MERCATOR);
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

export function rememberViewAdjustments(map: Map, settingsKey: string | null) {

  function restoreSettings(settingsKey: string) {
    const settings = sessionStorage.getItem(settingsKey);
    if (!settings) {
      return;
    }
    try {
      const {resolution, center, rotation} = JSON.parse(settings);
      const view = map.getView();
      view.setResolution(resolution);
      view.setCenter(center);
      view.setRotation(rotation);
    } catch (e) {
      console.error(e);
    }
  }

  function saveSettings(settingsKey: string) {
    const view = map.getView();
    const resolution = view.getResolution();
    const center = view.getCenter();
    const rotation = view.getRotation();
    sessionStorage.setItem(settingsKey, JSON.stringify({resolution, center, rotation}));
  }

  if (settingsKey) {
    restoreSettings(settingsKey);
    map.on('moveend', () => saveSettings(settingsKey));
  }
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

export function scaledTerritoryTextStyle(territoryNumber: string, feature: FeatureLike, resolution: number) {
  // Keep all territory numbers visible to not hide information.
  // Declutter them by shrinking the font in proportion to the geometry size.
  const extent = feature.getGeometry()!.getExtent();
  const geomWidthPx = getExtentWidth(extent) / resolution;
  const geomHeightPx = getExtentHeight(extent) / resolution;
  const charWidthPx = 10; // estimate based on the font size
  const textWidthPx = charWidthPx * territoryNumber.length;
  const scale = Math.max(geomWidthPx, geomHeightPx * 0.5) / textWidthPx; // geometry's height is less important for the text to fit on screen
  const text = territoryTextStyle(territoryNumber, '5mm');
  text.setScale(Math.min(scale, 1));
  return text;
}
