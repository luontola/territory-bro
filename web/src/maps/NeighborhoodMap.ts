// Copyright © 2015-2023 Esko Luontola
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
import OpenLayersMap from "./OpenLayersMap";

type Props = {
  territory: Territory;
  mapRaster: MapRaster;
  printout: boolean; // TODO: this should be always true and not needed here
};

export default class NeighborhoodMap extends OpenLayersMap<Props> {

  map: any;

  componentDidMount() {
    const {
      territory,
      mapRaster
    } = this.props;
    this.map = initNeighborhoodMap(this.elementRef.current, territory);
    this.map.setStreetsLayerRaster(mapRaster);
  }

  componentDidUpdate() {
    const {
      mapRaster
    } = this.props;
    this.map.setStreetsLayerRaster(mapRaster);
  }

  componentWillUnmount() {
    this.map.unmount()
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
      streetsLayer.setSource(mapRaster.source);
    },
    unmount() {
      map.setTarget(undefined)
    }
  };
}
