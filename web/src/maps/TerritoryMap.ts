// Copyright © 2015-2024 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import Map from "ol/Map";
import VectorLayer from "ol/layer/Vector";
import VectorSource from "ol/source/Vector";
import Style from "ol/style/Style";
import {
  findMapRasterById,
  makeControls,
  makeInteractions,
  makePrintoutView,
  makeStreetsLayer,
  makeView,
  MapRaster,
  territoryFillStyle,
  territoryStrokeStyle,
  wktToFeatures
} from "./mapOptions";
import {Territory} from "../api";
import OpenLayersMap from "./OpenLayersMap";
import Feature from 'ol/Feature';
import Geolocation from 'ol/Geolocation';
import Point from 'ol/geom/Point';
import {Circle as CircleStyle, Fill, Stroke} from 'ol/style';
import mapStyles from "./OpenLayersMap.module.css";

type Props = {
  territory: Territory;
  mapRaster: MapRaster;
  printout: boolean;
};

export default class TerritoryMap extends OpenLayersMap<Props> {

  map: any;
  prevMapRaster: any;

  componentDidMount() {
    const {
      territory,
      mapRaster,
      printout,
    } = this.props;
    this.prevMapRaster = mapRaster;
    this.map = initTerritoryMap(this.elementRef.current, territory, printout);
    this.map.setStreetsLayerRaster(mapRaster);
  }

  componentDidUpdate() {
    const {
      mapRaster
    } = this.props;
    if (this.prevMapRaster !== mapRaster) {
      this.prevMapRaster = mapRaster;
      this.map.setStreetsLayerRaster(mapRaster);
    }
  }

  componentWillUnmount() {
    this.map.unmount()
  }
}

export class TerritoryMapElement extends HTMLElement {
  constructor() {
    super();
    const location = this.getAttribute("location");
    const mapRaster = findMapRasterById(this.getAttribute("map-raster") || 'osmhd');
    const printout = !!this.getAttribute("printout");

    let className = mapStyles.root;
    if (printout) {
      className += " " + mapStyles.printout;
    }
    const root = document.createElement("div");
    root.setAttribute("class", className)
    this.appendChild(root)

    const map = initTerritoryMap(root, {location} as Territory, printout)
    map.setStreetsLayerRaster(mapRaster);
  }
}

function startGeolocation(map) {
  const geolocation = new Geolocation({
    tracking: true,
    trackingOptions: {
      enableHighAccuracy: true
    },
    projection: map.getView().getProjection()
  });

  const accuracyFeature = new Feature();
  geolocation.on('change:accuracyGeometry', function () {
    accuracyFeature.setGeometry(geolocation.getAccuracyGeometry());
  });

  const positionFeature = new Feature();
  positionFeature.setStyle(new Style({
    image: new CircleStyle({
      radius: 6,
      fill: new Fill({
        color: '#3399CC'
      }),
      stroke: new Stroke({
        color: '#fff',
        width: 2
      })
    })
  }));
  geolocation.on('change:position', function () {
    const coordinates = geolocation.getPosition();
    positionFeature.setGeometry(coordinates ? new Point(coordinates) : null);
  });

  new VectorLayer({
    map: map,
    source: new VectorSource({
      features: [accuracyFeature, positionFeature]
    })
  });
  return geolocation;
}

function initTerritoryMap(element: HTMLDivElement, territory: Territory, printout: boolean): any {
  const territoryWkt = territory.location;

  const territoryLayer = new VectorLayer({
    source: new VectorSource({
      features: wktToFeatures(territoryWkt)
    }),
    style: new Style({
      stroke: territoryStrokeStyle(),
      fill: territoryFillStyle()
    })
  });

  const streetsLayer = makeStreetsLayer();

  function resetZoom(map, opts) {
    map.getView().fit(territoryLayer.getSource().getExtent(), {
      padding: [20, 20, 20, 20],
      minResolution: 1.25, // prevent zooming too close, show more surrounding for small territories
      ...opts,
    });
  }

  const map = new Map({
    target: element,
    pixelRatio: 2, // render at high DPI for printing
    layers: [streetsLayer, territoryLayer],
    controls: makeControls({resetZoom, startGeolocation: printout ? null : startGeolocation}),
    interactions: makeInteractions(),
    view: printout ? makePrintoutView() : makeView({}),
  });
  resetZoom(map, {});

  return {
    setStreetsLayerRaster(mapRaster: MapRaster): void {
      streetsLayer.setSource(mapRaster.makeSource());
    },
    unmount() {
      map.setTarget(undefined)
    }
  };
}
