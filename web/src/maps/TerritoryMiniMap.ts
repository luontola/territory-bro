// Copyright © 2015-2023 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import Map from "ol/Map";
import View from "ol/View";
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
import minimapTerritoryUrl from "./minimap-territory.svg";

type Props = {
  territory: Territory;
  congregation: Congregation;
  printout: boolean; // TODO: this should be always true and not needed here
};

export default class TerritoryMiniMap extends OpenLayersMap<Props> {
  private map;

  componentDidMount() {
    const {
      territory,
      congregation,
    } = this.props;
    if (congregation.location) {
      this.map = initTerritoryMiniMap(this.elementRef.current, territory, congregation);
    } else {
      // TODO: this is never reached because of the default congregation boundary
      this.elementRef.current.innerText = "Error: Congregation boundary is not defined";
    }
  }

  componentWillUnmount() {
    this.map.unmount()
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
        src: minimapTerritoryUrl,
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

  const regionsLayer = new VectorLayer({
    source: new VectorSource({
      features: wktToFeatures(territory.enclosingRegion)
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
    layers: [streetLayer, regionsLayer, congregationLayer, territoryLayer],
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

  return {
    unmount() {
      map.setTarget(undefined)
    }
  };
}
