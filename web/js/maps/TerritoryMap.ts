// Copyright © 2015-2020 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import Map from "ol/Map";
import VectorLayer from "ol/layer/Vector";
import VectorSource from "ol/source/Vector";
import Style from "ol/style/Style";
import {
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

type Props = {
  territory: Territory;
  mapRaster: MapRaster;
  printout: boolean;
};

export default class TerritoryMap extends OpenLayersMap<Props> {

  map: any;

  componentDidMount() {
    const {
      territory,
      mapRaster,
      printout,
    } = this.props;
    this.map = initTerritoryMap(this.element, territory, printout);
    this.map.setStreetsLayerRaster(mapRaster);
  }

  componentDidUpdate() {
    const {
      mapRaster
    } = this.props;
    this.map.setStreetsLayerRaster(mapRaster);
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

  function resetZoom(map) {
    map.getView().fit(territoryLayer.getSource().getExtent(), {
      padding: [20, 20, 20, 20],
      minResolution: 1.25 // prevent zooming too close, show more surrounding for small territories
    });
  }

  const map = new Map({
    target: element,
    pixelRatio: 2, // render at high DPI for printing
    layers: [streetsLayer, territoryLayer],
    controls: makeControls({resetZoom, startGeolocation: printout ? null : startGeolocation}),
    interactions: makeInteractions(),
    view: printout ? makePrintoutView() : makeView({})
  });
  resetZoom(map);

  return {
    setStreetsLayerRaster(mapRaster: MapRaster): void {
      streetsLayer.setSource(mapRaster.source);
    }
  };
}
