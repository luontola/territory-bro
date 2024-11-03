import Map from "ol/Map";
import View from "ol/View";
import VectorLayer from "ol/layer/Vector";
import VectorSource from "ol/source/Vector";
import Style from "ol/style/Style";
import Stroke from "ol/style/Stroke";
import Fill from "ol/style/Fill";
import Circle from "ol/style/Circle";
import {fromLonLat} from "ol/proj";
import WKT from "ol/format/WKT";
import {LocationOnly, makeStreetsLayer, MapRaster, wktToFeatures} from "./mapOptions.ts";
import {OpenLayersMapElement} from "./OpenLayersMap.ts";
import MultiPolygon from "ol/geom/MultiPolygon";

export class TerritoryMiniMapElement extends OpenLayersMapElement {
  createMap({root, mapRaster}) {
    const territory = {
      location: this.getAttribute("territory-location")!,
      enclosingMinimapViewport: this.getAttribute("enclosing-minimap-viewport"),
      enclosingRegion: this.getAttribute("enclosing-region"),
    };
    const congregation = {
      location: this.getAttribute("congregation-boundary"),
    };
    if (congregation.location) {
      const map = initTerritoryMiniMap(root, territory, congregation);
      map.setStreetsLayerRaster(mapRaster);
      return map;
    }
  }
}

function getCenterPoint(multiPolygonWkt: string) {
  const wkt = new WKT();
  const multiPolygon = wkt.readFeature(multiPolygonWkt).getGeometry() as MultiPolygon;
  const centerPoint = multiPolygon.getInteriorPoints().getPoint(0);
  return wkt.writeGeometry(centerPoint);
}

function initTerritoryMiniMap(element: HTMLElement,
                              territory: {
                                location: string;
                                enclosingMinimapViewport: string | null;
                                enclosingRegion: string | null
                              },
                              congregation: LocationOnly) {
  const territoryLayer = new VectorLayer({
    source: new VectorSource({
      features: wktToFeatures(getCenterPoint(territory.location))
    }),
    style: new Style({
      image: new Circle({
        radius: 3.5,
        fill: new Fill({
          color: 'rgba(0, 0, 0, 1.0)'
        })
      })
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

  const streetsLayer = makeStreetsLayer();

  const map = new Map({
    target: element,
    pixelRatio: 2, // render at high DPI for printing
    layers: [streetsLayer, regionsLayer, congregationLayer, territoryLayer],
    controls: [],
    interactions: [],
    view: new View({
      center: fromLonLat([0.0, 0.0]),
      zoom: 1
    })
  });
  map.getView().fit(viewportSource.getExtent(), {
    padding: [1, 1, 1, 1], // minimum padding where the congregation lines still show up
  });

  return {
    setStreetsLayerRaster(mapRaster: MapRaster) {
      streetsLayer.setSource(mapRaster.makeSource());
    },
    unmount() {
      map.setTarget(undefined)
    }
  };
}
