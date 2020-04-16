// Copyright © 2015-2019 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import {Map, View} from "ol";
import VectorLayer from "ol/layer/Vector";
import VectorSource from "ol/source/Vector";
import Style from "ol/style/Style";
import Stroke from "ol/style/Stroke";
import Fill from "ol/style/Fill";
import Icon from "ol/style/Icon";
import {fromLonLat} from "ol/proj";
import WKT from "ol/format/WKT";
import {makeStreetsLayer, wktToFeatures} from "./mapOptions";
import {Congregation, Territory} from "../api";
import OpenLayersMap from "./OpenLayersMap";

type Props = {
  territory: Territory;
  congregation: Congregation;
};

export default class TerritoryMiniMap extends OpenLayersMap<Props> {

  componentDidMount() {
    const {
      territory,
      congregation
    } = this.props;
    if (congregation.location) {
      initTerritoryMiniMap(this.element, territory, congregation);
    } else {
      // TODO: this is never reached because of the default congregation boundary
      this.element.innerText = "Error: Congregation boundary is not defined";
    }
  }
}

function getCenterPoint(multiPolygon: string) {
  const wkt = new WKT();
  const centerPoint = wkt.readFeature(multiPolygon).getGeometry().getInteriorPoints().getPoint(0);
  return wkt.writeGeometry(centerPoint);
}

function initTerritoryMiniMap(element: HTMLElement, territory: Territory, congregation: Congregation) {
  const territoryLayer = new VectorLayer({
    source: new VectorSource({
      features: wktToFeatures(getCenterPoint(territory.location))
    }),
    style: new Style({
      image: new Icon({
        src: '/img/minimap-territory.svg',
        imgSize: [10, 10],
        snapToPixel: true
      })
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
    features: wktToFeatures(territory.enclosingMinimapViewport || congregation.location)
  });

  const congregationLayer = new VectorLayer({
    source: new VectorSource({
      features: wktToFeatures(congregation.location)
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
      features: wktToFeatures(territory.enclosingSubregion)
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
  map.getView().fit(viewportSource.getExtent(), {
    padding: [1, 1, 1, 1], // minimum padding where the congregation lines still show up
    constrainResolution: false
  });
}
