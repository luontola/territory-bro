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
  onClick: (string) => void;
};

export default class TerritoryListMap extends OpenLayersMap<Props> {

  map: any;

  componentDidMount() {
    const {
      congregation,
      territories,
      onClick,
    } = this.props;
    this.map = initRegionMap(this.element, congregation, onClick);
    this.map.setTerritories(territories);
  }

  componentDidUpdate() {
    const {
      territories
    } = this.props;
    this.map.setTerritories(territories);
  }
}

function initRegionMap(element: HTMLDivElement, congregation: Congregation, onClick: (string) => void): any {
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

  map.on('click', event => {
    // XXX: only finds feature if clicking its label or border, but not when clicking inside the borders
    const features = map.getFeaturesAtPixel(event.pixel, {layerFilter: layer => layer === territoryLayer});
    if (features.length === 1) { // ignore ambiguous clicks when labels overlap; must zoom closer and click only one
      const feature = features[0];
      const territoryId = feature.get('territoryId');
      if (territoryId) {
        onClick(territoryId);
      }
    }
  });

  return {
    setTerritories(territories: Array<Territory>): void {
      const features = territories.map(function (territory) {
        const feature = wktToFeature(territory.location);
        feature.set('territoryId', territory.id);
        feature.set('number', territory.number);
        return feature;
      });
      territoryLayer.setSource(new VectorSource({features}))
      resetZoom(map, {duration: 300});
    }
  };
}
