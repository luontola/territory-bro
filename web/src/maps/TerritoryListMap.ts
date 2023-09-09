// Copyright © 2015-2023 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import Map from "ol/Map";
import VectorLayer from "ol/layer/Vector";
import VectorSource from "ol/source/Vector";
import Style from "ol/style/Style";
import Stroke from "ol/style/Stroke";
import Fill from "ol/style/Fill";
import {
  makeControls,
  makeInteractions,
  makeStreetsLayer,
  makeView,
  territoryStrokeStyle,
  territoryTextStyle,
  wktToFeature,
  wktToFeatures
} from "./mapOptions";
import {Congregation, Territory} from "../api";
import OpenLayersMap from "./OpenLayersMap";
import {isEmpty} from "ol/extent";
import {getPageState, setPageState} from "../util";
import {isEqual} from "lodash-es";

type Props = {
  congregation: Congregation;
  territories: Territory[];
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
    this.map = initMap(this.elementRef.current, congregation, territories, onClick);
  }

  componentDidUpdate(prevProps, _prevState) {
    const {
      territories
    } = this.props;
    if (!isEqual(territories, prevProps.territories)) {
      this.map.updateTerritories(territories);
    }
  }

  componentWillUnmount() {
    this.map.unmount()
  }
}

function loanableTerritoryStroke(loaned) {
  const stroke = territoryStrokeStyle();
  if (typeof loaned === 'boolean') {
    stroke.setColor(loaned ?
      'rgba(255, 0, 0, 0.6)' :
      'rgba(0, 0, 255, 0.6)')
  }
  return stroke;
}

function loanableTerritoryFill(loaned, staleness) {
  const fill = new Fill({
    color: 'rgba(255, 0, 0, 0.0)',
  });
  if (typeof loaned === 'boolean') {
    fill.setColor(loaned ?
      (staleness < 3 ? 'rgba(150, 150, 150, 0.2)' :
        staleness < 6 ? 'rgba(255, 150, 0, 0.2)' :
          'rgba(255, 0, 0, 0.2)') :
      (staleness < 3 ? 'rgba(150, 150, 150, 0.2)' :
        staleness < 6 ? 'rgba(0, 200, 255, 0.2)' :
          'rgba(0, 0, 255, 0.2)'));
  }
  return fill;
}

function initMap(element: HTMLDivElement,
                 congregation: Congregation,
                 territories: Territory[],
                 onClick: (string) => void): any {
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
      const number = feature.get('number');
      const loaned = feature.get('loaned');
      const staleness = feature.get('staleness');

      const style = new Style({
        stroke: loanableTerritoryStroke(loaned),
        fill: loanableTerritoryFill(loaned, staleness),
        text: territoryTextStyle(number, '5mm')
      });
      return [style];
    }
  });

  function setTerritories(territories) {
    const features = territories.map(function (territory) {
      const feature = wktToFeature(territory.location);
      feature.set('territoryId', territory.id);
      feature.set('number', territory.number);
      feature.set('loaned', territory.loaned);
      feature.set('staleness', territory.staleness);
      return feature;
    });
    territoryLayer.setSource(new VectorSource({features}))
  }

  setTerritories(territories);

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
    view: makeView({}),
  });
  resetZoom(map, {});

  const mapState = getPageState('map');
  if (mapState) {
    map.getView().setCenter(mapState.center)
    map.getView().setZoom(mapState.zoom)
    map.getView().setRotation(mapState.rotation)
  }
  map.on('moveend', _event => {
    setPageState('map', {
      center: map.getView().getCenter(),
      zoom: map.getView().getZoom(),
      rotation: map.getView().getRotation(),
    });
  })

  map.on('singleclick', event => {
    // the feature needs to have a fill, or else getFeaturesAtPixel finds it only if the click hit its stoke or text
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
    updateTerritories(territories: Territory[]): void {
      setTerritories(territories);
      resetZoom(map, {duration: 300});
    },
    unmount() {
      map.setTarget(undefined)
    }
  };
}
