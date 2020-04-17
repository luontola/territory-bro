// Copyright © 2015-2020 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import {Map, View} from "ol";
import VectorLayer from "ol/layer/Vector";
import VectorSource from "ol/source/Vector";
import Style from "ol/style/Style";
import Stroke from "ol/style/Stroke";
import {fromLonLat} from "ol/proj";
import {
  makeControls,
  makeStreetsLayer,
  MapRaster,
  territoryStrokeStyle,
  territoryTextStyle,
  wktToFeature,
  wktToFeatures
} from "./mapOptions";
import {Congregation, Region, Territory} from "../api";
import OpenLayersMap from "./OpenLayersMap";

type Props = {
  region: Region | Congregation;
  territories: Array<Territory>;
  mapRaster: MapRaster;
};

export default class RegionMap extends OpenLayersMap<Props> {

  map: any;

  componentDidMount() {
    const {
      region,
      territories,
      mapRaster
    } = this.props;
    this.map = initRegionMap(this.element, region, territories);
    this.map.setStreetsLayerRaster(mapRaster);
  }

  componentDidUpdate() {
    const {
      mapRaster
    } = this.props;
    this.map.setStreetsLayerRaster(mapRaster);
  }
}

function initRegionMap(element: HTMLDivElement, region: Region | Congregation, territories: Array<Territory>): any {
  const regionLayer = new VectorLayer({
    source: new VectorSource({
      features: wktToFeatures(region.location)
    }),
    style: new Style({
      stroke: new Stroke({
        color: 'rgba(0, 0, 0, 0.6)',
        width: 4.0
      })
    })
  });

  const territoryLayer = new VectorLayer({
    source: new VectorSource({
      features: territories.map(function (territory) {
        const feature = wktToFeature(territory.location);
        feature.set('number', territory.number);
        return feature;
      })
    }),
    style: function (feature, resolution) {
      const style = new Style({
        stroke: territoryStrokeStyle(),
        text: territoryTextStyle(feature.get('number'), '5mm')
      });
      return [style];
    }
  });

  const streetsLayer = makeStreetsLayer();

  const map = new Map({
    target: element,
    pixelRatio: 2, // render at high DPI for printing
    layers: [streetsLayer, regionLayer, territoryLayer],
    controls: makeControls(),
    view: new View({
      center: fromLonLat([0.0, 0.0]),
      zoom: 1,
      minResolution: 0.1,
      zoomFactor: 1.1 // zoom in small steps to enable fine tuning
    })
  });
  map.getView().fit(regionLayer.getSource().getExtent(), {
    padding: [5, 5, 5, 5],
    minResolution: 3.0
  });

  return {
    setStreetsLayerRaster(mapRaster: MapRaster): void {
      streetsLayer.setSource(mapRaster.source);
    }
  };
}
