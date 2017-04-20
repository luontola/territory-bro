// Copyright © 2015-2017 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import "openlayers/dist/ol.css";
import ol from "openlayers";
import type {Region, Territory} from "./api";

export function wktToFeature(wkt: string): ol.Feature {
  const feature = new ol.format.WKT().readFeature(wkt);
  feature.getGeometry().transform('EPSG:4326', 'EPSG:3857');
  return feature;
}

function makeStreetsLayer() {
  return new ol.layer.Tile({
    //source: new ol.source.OSM()
    // high DPI spike
    source: new ol.source.XYZ({
      url: '//a.osm.rrze.fau.de/osmhd/{z}/{x}/{y}.png',
      tileSize: [512, 512],
      attributions: [
        ol.source.OSM.ATTRIBUTION
      ]
    })
  });
}

function makeControls() {
  const attribution = new ol.control.Attribution({
    className: 'map-attribution',
    collapsible: false
  });
  return ol.control.defaults({attribution: false}).extend([attribution]);
}

// visual style

function territoryStrokeStyle() {
  return new ol.style.Stroke({
    color: 'rgba(255, 0, 0, 0.6)',
    width: 2.0
  });
}

function territoryFillStyle() {
  return new ol.style.Fill({
    color: 'rgba(255, 0, 0, 0.1)'
  });
}

function territoryTextStyle(territoryNumber: string, fontSize: string) {
  return new ol.style.Text({
    text: territoryNumber,
    font: 'bold ' + fontSize + ' sans-serif',
    fill: new ol.style.Fill({color: 'rgba(0, 0, 0, 1.0)'}),
    stroke: new ol.style.Stroke({color: 'rgba(255, 255, 255, 1.0)', width: 3.0})
  });
}

// map constructors

export function initTerritoryMap(element: HTMLDivElement,
                                 territory: Territory): void {
  const territoryWkt = territory.location;
  const territoryLayer = new ol.layer.Vector({
    source: new ol.source.Vector({
      features: [wktToFeature(territoryWkt)]
    }),
    style: new ol.style.Style({
      stroke: territoryStrokeStyle(),
      fill: territoryFillStyle()
    })
  });

  const map = new ol.Map({
    target: element,
    pixelRatio: 2, // render at high DPI for printing
    layers: [makeStreetsLayer(), territoryLayer],
    controls: makeControls(),
    view: new ol.View({
      center: ol.proj.fromLonLat([0.0, 0.0]),
      zoom: 1,
      minResolution: 1.25, // prevent zooming too close, show more surrounding for small territories
      zoomFactor: 1.1 // zoom in small steps to enable fine tuning
    })
  });
  map.getView().fit(
    territoryLayer.getSource().getExtent(),
    {
      padding: [20, 20, 20, 20]
    }
  );
}

export function initTerritoryMiniMap(element: HTMLDivElement,
                                     territory: Territory,
                                     regions: Array<Region>): void {
  const wkt = new ol.format.WKT();
  const centerPoint = wkt.readFeature(territory.location).getGeometry().getInteriorPoint();
  const territoryWkt = wkt.writeGeometry(centerPoint);
  // TODO: handle it gracefully if one of the following is not initialized
  let viewportWkt = '';
  let congregationWkt = '';
  let subregionsWkt = '';
  const territoryCoordinate = centerPoint.getCoordinates();
  regions.forEach(region => {
    const regionGeom = wkt.readFeature(region.location).getGeometry();
    if (regionGeom.intersectsCoordinate(territoryCoordinate)) {
      if (region.minimap_viewport) {
        viewportWkt = region.location;
      }
      if (region.congregation) {
        congregationWkt = region.location;
      }
      if (region.subregion) {
        // TODO: support multiple (nested) subregions
        subregionsWkt = region.location;
      }
    }
  });

  const territoryLayer = new ol.layer.Vector({
    source: new ol.source.Vector({
      features: [wktToFeature(territoryWkt)]
    }),
    style: new ol.style.Style({
      image: new ol.style.Circle({
        radius: 3.5,
        fill: new ol.style.Fill({
          color: 'rgba(0, 0, 0, 1.0)'
        })
      })
    })
  });

  const viewportSource = new ol.source.Vector({
    features: [wktToFeature(viewportWkt)]
  });

  const congregationLayer = new ol.layer.Vector({
    source: new ol.source.Vector({
      features: [wktToFeature(congregationWkt)]
    }),
    style: new ol.style.Style({
      stroke: new ol.style.Stroke({
        color: 'rgba(0, 0, 0, 1.0)',
        width: 1.0
      })
    })
  });

  const subregionsLayer = new ol.layer.Vector({
    source: new ol.source.Vector({
      features: [wktToFeature(subregionsWkt)]
    }),
    style: new ol.style.Style({
      stroke: new ol.style.Stroke({
        color: 'rgba(0, 0, 0, 1.0)',
        width: 1.0,
        lineDash: [1, 4],
        lineCap: 'round'
      })
    })
  });

  const map = new ol.Map({
    target: element,
    pixelRatio: 2, // render at high DPI for printing
    layers: [makeStreetsLayer(), subregionsLayer, congregationLayer, territoryLayer],
    controls: [],
    interactions: [],
    view: new ol.View({
      center: ol.proj.fromLonLat([0.0, 0.0]),
      zoom: 1
    })
  });
  map.getView().fit(
    viewportSource.getExtent(),
    {
      padding: [1, 1, 1, 1], // minimum padding where the congregation lines still show up
      constrainResolution: false
    }
  );
}

export function initNeighborhoodMap(element: HTMLDivElement,
                                    territory: Territory): void {
  const territoryNumber = territory.number;
  const territoryWkt = territory.location;

  const territoryLayer = new ol.layer.Vector({
    source: new ol.source.Vector({
      features: [wktToFeature(territoryWkt)]
    }),
    style: new ol.style.Style({
      stroke: territoryStrokeStyle(),
      fill: territoryFillStyle(),
      text: territoryTextStyle(territoryNumber, '180%')
    })
  });

  const map = new ol.Map({
    target: element,
    pixelRatio: 2, // render at high DPI for printing
    layers: [makeStreetsLayer(), territoryLayer],
    controls: makeControls(),
    view: new ol.View({
      center: ol.proj.fromLonLat([0.0, 0.0]),
      zoom: 1,
      minResolution: 1.25, // prevent zooming too close, show more surrounding for small territories
      zoomFactor: 1.1 // zoom in small steps to enable fine tuning
    })
  });
  map.getView().fit(
    territoryLayer.getSource().getExtent(),
    {
      padding: [5, 5, 5, 5],
      minResolution: 3.0
    }
  );
}

export function initRegionMap(element: HTMLDivElement,
                              region: Region,
                              territories: Array<Territory>): void {
  const regionLayer = new ol.layer.Vector({
    source: new ol.source.Vector({
      features: [wktToFeature(region.location)]
    }),
    style: new ol.style.Style({
      stroke: new ol.style.Stroke({
        color: 'rgba(0, 0, 0, 0.6)',
        width: 4.0
      })
    })
  });

  const territoryLayer = new ol.layer.Vector({
    source: new ol.source.Vector({
      features: territories.map(function (territory) {
        const feature = wktToFeature(territory.location);
        feature.set('number', territory.number);
        return feature;
      })
    }),
    style: function (feature, resolution) {
      const style = new ol.style.Style({
        stroke: territoryStrokeStyle(),
        text: territoryTextStyle(feature.get('number'), '5mm')
      });
      return [style];
    }
  });

  const map = new ol.Map({
    target: element,
    pixelRatio: 2, // render at high DPI for printing
    layers: [makeStreetsLayer(), regionLayer, territoryLayer],
    controls: makeControls(),
    view: new ol.View({
      center: ol.proj.fromLonLat([0.0, 0.0]),
      zoom: 1,
      minResolution: 1.25, // prevent zooming too close, show more surrounding for small territories
      zoomFactor: 1.1 // zoom in small steps to enable fine tuning
    })
  });
  map.getView().fit(
    regionLayer.getSource().getExtent(),
    {
      padding: [5, 5, 5, 5],
      minResolution: 3.0
    }
  );
}
