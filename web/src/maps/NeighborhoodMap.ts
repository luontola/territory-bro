// Copyright © 2015-2024 Esko Luontola
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
  MapRaster,
  territoryFillStyle,
  territoryStrokeStyle,
  territoryTextStyle,
  wktToFeatures
} from "./mapOptions";
import {Territory} from "../api";
import {OpenLayersMapElement} from "./OpenLayersMap";

export class NeighborhoodMapElement extends OpenLayersMapElement {
  createMap({root, mapRaster}) {
    const territory = {
      number: this.getAttribute("territory-number"),
      location: this.getAttribute("territory-location")
    };
    const map = initNeighborhoodMap(root, territory as Territory)
    map.setStreetsLayerRaster(mapRaster);
    return map
  }
}

function initNeighborhoodMap(element: HTMLDivElement, territory: Territory): any {
  const territoryNumber = territory.number;
  const territoryWkt = territory.location;

  const territoryLayer = new VectorLayer({
    source: new VectorSource({
      features: wktToFeatures(territoryWkt)
    }),
    style: new Style({
      stroke: territoryStrokeStyle(),
      fill: territoryFillStyle(),
      text: territoryTextStyle(territoryNumber, '180%')
    })
  });

  const streetsLayer = makeStreetsLayer();

  function resetZoom(map, opts) {
    map.getView().fit(territoryLayer.getSource().getExtent(), {
      padding: [5, 5, 5, 5],
      minResolution: 3.0,
      ...opts,
    });
  }

  const map = new Map({
    target: element,
    pixelRatio: 2, // render at high DPI for printing
    layers: [streetsLayer, territoryLayer],
    controls: makeControls({resetZoom}),
    interactions: makeInteractions(),
    view: makePrintoutView(),
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
