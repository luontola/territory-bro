var territoryBro = (function () {

  function wktToFeature(wkt) {
    var feature = new ol.format.WKT().readFeature(wkt);
    feature.getGeometry().transform('EPSG:4326', 'EPSG:3857');
    return feature;
  }

  function useHighDpiMaps(map) {
    // render at a higher zoom level for high DPI
    var updateSize = map.updateSize;
    map.updateSize = function () {
      updateSize.call(this);
      this.setSize(this.getSize().map(function (x) {
        return x * 2;
      }));
    };
    map.updateSize();
    // XXX: the canvas size resets when the window is resized
    window.addEventListener('resize', function (e) {
      map.updateSize();
    });
  }

  function initTerritoryMap(elementId, territoryWkt) {
    var territoryLayer = new ol.layer.Vector({
      source: new ol.source.Vector({
        features: [wktToFeature(territoryWkt)]
      }),
      style: new ol.style.Style({
        stroke: new ol.style.Stroke({
          color: 'rgba(255, 0, 0, 1.0)',
          width: 3
        }),
        fill: new ol.style.Fill({
          color: 'rgba(255, 0, 0, 0.1)'
        })
      })
    });

    var streetsLayer = new ol.layer.Tile({
      source: new ol.source.OSM()
    });

    var map = new ol.Map({
      target: elementId,
      pixelRatio: 2, // render at high DPI for printing
      layers: [streetsLayer, territoryLayer],
      controls: [],
      interactions: [],
      view: new ol.View({
        center: ol.proj.fromLonLat([0.0, 0.0]),
        zoom: 1,
        // show more surrounding area for small territories
        maxZoom: 18
      })
    });
    useHighDpiMaps(map);
    map.getView().fit(territoryLayer.getSource().getExtent(), map.getSize());
  }

  function initTerritoryMiniMap(elementId, territoryWkt, regionWkt) {
    var territoryLayer = new ol.layer.Vector({
      source: new ol.source.Vector({
        features: [wktToFeature(territoryWkt)]
      }),
      style: new ol.style.Style({
        image: new ol.style.Circle({
          radius: 8,
          fill: new ol.style.Fill({
            color: 'rgba(0, 0, 0, 1.0)'
          })
        })
      })
    });

    var regionLayer = new ol.layer.Vector({
      source: new ol.source.Vector({
        features: [wktToFeature(regionWkt)]
      }),
      style: new ol.style.Style({
        stroke: new ol.style.Stroke({
          color: 'rgba(0, 0, 0, 1.0)',
          width: 1.5
        })
      })
    });

    // TODO: remove me; streets layer is only for debugging
    var streetsLayer = new ol.layer.Tile({
      source: new ol.source.OSM()
    });

    var map = new ol.Map({
      target: elementId,
      pixelRatio: 2, // render at high DPI for printing
      layers: [streetsLayer, regionLayer, territoryLayer],
      controls: [],
      interactions: [],
      view: new ol.View({
        center: ol.proj.fromLonLat([0.0, 0.0]),
        zoom: 1
      })
    });
    useHighDpiMaps(map);
    map.getView().fit(regionLayer.getSource().getExtent(), map.getSize());
  }

  return {
    initTerritoryMap: initTerritoryMap,
    initTerritoryMiniMap: initTerritoryMiniMap
  };
})();
