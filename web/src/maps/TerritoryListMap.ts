// Copyright © 2015-2024 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import Map from "ol/Map";
import VectorLayer from "ol/layer/Vector";
import VectorSource from "ol/source/Vector";
import Style from "ol/style/Style";
import Stroke from "ol/style/Stroke";
import Fill from "ol/style/Fill";
import {
  LocationOnly,
  makeControls,
  makeInteractions,
  makeStreetsLayer,
  makeView,
  MapRaster,
  TerritoryPlus,
  territoryStrokeStyle,
  territoryTextStyle,
  wktToFeature,
  wktToFeatures
} from "./mapOptions.ts";
import {OpenLayersMapElement} from "./OpenLayersMap.ts";
import {isEmpty as isEmptyExtent} from "ol/extent";
import {getPageState, setPageState} from "../util.ts";

export class TerritoryListMapElement extends OpenLayersMapElement {
  static observedAttributes = ['visible-territories'];

  connectedCallback() {
    const onTerritorySearch = window.onTerritorySearch;
    if (onTerritorySearch) {
      // This will cause the search to populate the visible-territories attribute.
      // It's important to do this before the map has been created: When the user
      // navigates back in history, the map zoom and position should be restored
      // to what it was previously, instead of resetting the zoom to its defaults.
      // After the map has been created, changing the visible-territories attribute
      // will automatically reset the zoom.
      onTerritorySearch()
    }
    super.connectedCallback();
  }

  attributeChangedCallback(name: string, _oldValue: string | null, newValue: string | null) {
    if (name === 'visible-territories') {
      if (this.map) { // the map might not yet have been created
        this.map.updateVisibleTerritories(JSON.parse(newValue ?? "[]"));
      }
    }
  }

  createMap({root, mapRaster}) {
    const jsonData = this.querySelector("template.json-data") as HTMLTemplateElement | null;
    const data = JSON.parse(jsonData?.content.textContent ?? "{}");
    const congregationBoundary = data.congregationBoundary;
    const territories = data.territories ?? [];
    const visibleTerritories: string[] = JSON.parse(this.getAttribute("visible-territories") ?? "[]");

    const congregation = {
      location: congregationBoundary
    };
    const onClick = (territoryId: string) => {
      document.location.href = `${document.location.pathname}/${territoryId}`
    }
    const map = initMap(root, congregation, territories, visibleTerritories, onClick);
    map.setStreetsLayerRaster(mapRaster);
    return map;
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
                 congregation: LocationOnly,
                 territories: TerritoryPlus[],
                 visibleTerritories: string[],
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

  const allTerritories = territories.map(territoryToFeature);
  setVisibleTerritories(visibleTerritories);

  function territoryToFeature(territory: TerritoryPlus) {
    const feature = wktToFeature(territory.location);
    feature.set('territoryId', territory.id);
    feature.set('number', territory.number);
    feature.set('loaned', territory.loaned);
    feature.set('staleness', territory.staleness);
    return feature;
  }

  function setVisibleTerritories(territoryIds: string[]) {
    const visible = new Set(territoryIds);
    const features = allTerritories.filter(feature => visible.has(feature.get('territoryId')));
    territoryLayer.setSource(new VectorSource({features}))
  }

  const streetsLayer = makeStreetsLayer();

  function resetZoom(map, opts) {
    // by default fit all territories
    let extent = territoryLayer.getSource()!.getExtent();
    if (isEmptyExtent(extent)) {
      // if there are no territories, fit congregation boundaries
      extent = congregationLayer.getSource()!.getExtent();
    }
    if (isEmptyExtent(extent)) {
      // if there is no congregation boundary, skip fitting (it would just throw an error)
      return;
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

  // This seemingly duplicates rememberViewAdjustments, but a big
  // difference is that this is stored in history API instead of
  // session storage. When a user arrives to this page through
  // the navigation menu instead of back button, then the map zoom
  // should be reset.
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
    setStreetsLayerRaster(mapRaster: MapRaster): void {
      streetsLayer.setSource(mapRaster.makeSource());
    },
    updateVisibleTerritories(territoryIds: string[]) {
      setVisibleTerritories(territoryIds);
      resetZoom(map, {duration: 300});
    },
    unmount() {
      map.setTarget(undefined)
    }
  };
}
