// Copyright © 2015-2022 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import Map from "ol/Map";
import VectorLayer from "ol/layer/Vector";
import VectorSource from "ol/source/Vector";
import Style from "ol/style/Style";
import Stroke from "ol/style/Stroke";
import {
  makeControls,
  makeInteractions,
  makePrintoutView,
  makeStreetsLayer,
  territoryStrokeStyle,
  territoryTextStyle,
  wktToFeature,
  wktToFeatures
} from "./mapOptions";
import {Congregation, Territory} from "../api";
import OpenLayersMap from "./OpenLayersMap";
import {isEmpty} from "ol/extent";

type Props = {
  congregation: Congregation;
  territories: Array<Territory>;
};

export default class TerritoryListMap extends OpenLayersMap<Props> {

  map: any;

  componentDidMount() {
    const {
      congregation,
      territories,
    } = this.props;
    this.map = initRegionMap(this.element, congregation);
    this.map.setTerritories(territories);
  }

  componentDidUpdate() {
    const {
      territories
    } = this.props;
    this.map.setTerritories(territories);
  }
}

function initRegionMap(element: HTMLDivElement, congregation: Congregation): any {
  const congregationLayer = new VectorLayer({
    source: new VectorSource({
      features: wktToFeatures(congregation.location)
    }),
    style: new Style({
      stroke: new Stroke({
        color: 'rgba(0, 0, 0, 0.6)',
        width: 4.0
      })
    })
  });

  const territoryLayer = new VectorLayer({
    source: new VectorSource({}),
    style: function (feature, resolution) {
      const style = new Style({
        stroke: territoryStrokeStyle(),
        text: territoryTextStyle(feature.get('number'), '5mm')
      });
      return [style];
    }
  });

  const streetsLayer = makeStreetsLayer();

  function resetZoom(map, opts) {
    let extent = territoryLayer.getSource().getExtent();
    if (isEmpty(extent)) {
      extent = congregationLayer.getSource().getExtent();
    }
    const padding = 50;
    map.getView().fit(extent, {
      padding: [padding, padding, padding, padding],
      minResolution: 3.0,
      ...opts,
    });
  }

  const map = new Map({
    target: element,
    pixelRatio: 2, // render at high DPI for printing
    layers: [streetsLayer, congregationLayer, territoryLayer],
    controls: makeControls({resetZoom}),
    interactions: makeInteractions(),
    view: makePrintoutView(),
  });
  resetZoom(map, {});

  return {
    setTerritories(territories: Array<Territory>): void {
      const features = territories.map(function (territory) {
        const feature = wktToFeature(territory.location);
        feature.set('number', territory.number);
        return feature;
      });
      territoryLayer.setSource(new VectorSource({features}))
      resetZoom(map, {duration: 300});
    }
  };
}
