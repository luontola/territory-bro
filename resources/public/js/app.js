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
          color: 'rgba(255, 0, 0, 0.6)',
          width: 2.0
        }),
        fill: new ol.style.Fill({
          color: 'rgba(255, 0, 0, 0.1)'
        })
      })
    });

    var streetsLayer = new ol.layer.Tile({
      source: new ol.source.OSM()
    });
    // high DPI spike
    streetsLayer = new ol.layer.Tile({
      source: new ol.source.XYZ({
        url: '//a.osm.rrze.fau.de/osmhd/{z}/{x}/{y}.png',
        tileSize: [512, 512]
      })
    });

    var map = new ol.Map({
      target: elementId,
      pixelRatio: 2, // render at high DPI for printing
      layers: [streetsLayer, territoryLayer],
      view: new ol.View({
        center: ol.proj.fromLonLat([0.0, 0.0]),
        zoom: 1,
        minResolution: 1.25, // prevent zooming too close, show more surrounding for small territories
        zoomFactor: 1.1 // zoom in small steps to enable fine tuning
      })
    });
    //useHighDpiMaps(map);
    map.getView().fit(
      territoryLayer.getSource().getExtent(),
      map.getSize(),
      {
        padding: [20, 20, 20, 20]
      }
    );
  }

  function initTerritoryMiniMap(elementId, territoryWkt, regionWkt) {
    var territoryLayer = new ol.layer.Vector({
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

    var regionLayer = new ol.layer.Vector({
      source: new ol.source.Vector({
        features: [wktToFeature(regionWkt)]
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

    var streetsLayer = new ol.layer.Tile({
      source: new ol.source.OSM()
    });
    // high DPI spike
    streetsLayer = new ol.layer.Tile({
      source: new ol.source.XYZ({
        url: '//a.osm.rrze.fau.de/osmhd/{z}/{x}/{y}.png',
        tileSize: [512, 512]
      })
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
    //useHighDpiMaps(map);
    map.getView().fit(
      regionLayer.getSource().getExtent(),
      map.getSize(),
      {
        padding: [5, 5, 5, 5],
        constrainResolution: false
      }
    );
  }

  function initNeighborhoodMap(elementId, territoryNumber, territoryWkt) {
    var territoryLayer = new ol.layer.Vector({
      source: new ol.source.Vector({
        features: [wktToFeature(territoryWkt)]
      }),
      style: new ol.style.Style({
        stroke: new ol.style.Stroke({
          color: 'rgba(255, 0, 0, 0.6)',
          width: 2.0
        }),
        fill: new ol.style.Fill({
          color: 'rgba(255, 0, 0, 0.1)'
        }),
        text: new ol.style.Text({
          text: territoryNumber,
          font: 'bold 180% sans-serif',
          fill: new ol.style.Fill({color: 'rgba(0, 0, 0, 1.0)'}),
          stroke: new ol.style.Stroke({color: 'rgba(255, 255, 255, 1.0)', width: 3.0})
        })
      })
    });

    var streetsLayer = new ol.layer.Tile({
      source: new ol.source.OSM()
    });
    // high DPI spike
    streetsLayer = new ol.layer.Tile({
      source: new ol.source.XYZ({
        url: '//a.osm.rrze.fau.de/osmhd/{z}/{x}/{y}.png',
        tileSize: [512, 512]
      })
    });

    var map = new ol.Map({
      target: elementId,
      pixelRatio: 2, // render at high DPI for printing
      layers: [streetsLayer, territoryLayer],
      view: new ol.View({
        center: ol.proj.fromLonLat([0.0, 0.0]),
        zoom: 1,
        minResolution: 1.25, // prevent zooming too close, show more surrounding for small territories
        zoomFactor: 1.1 // zoom in small steps to enable fine tuning
      })
    });
    //useHighDpiMaps(map);
    map.getView().fit(
      territoryLayer.getSource().getExtent(),
      map.getSize(),
      {
        padding: [5, 5, 5, 5],
        minResolution: 3.0
      }
    );
  }

  function initRegionMap(elementId, regionWkt, territories) {
    console.log(elementId, regionWkt, territories);

    var regionLayer = new ol.layer.Vector({
      source: new ol.source.Vector({
        features: [wktToFeature(regionWkt)]
      }),
      style: new ol.style.Style({
        stroke: new ol.style.Stroke({
          color: 'rgba(0, 0, 0, 0.6)',
          width: 4.0
        })
      })
    });

    var territoryLayer = new ol.layer.Vector({
      source: new ol.source.Vector({
        features: territories.map(function (territory) {
          var feature = wktToFeature(territory.location);
          feature.set('number', territory.number);
          return feature;
        })
      }),
      style: function (feature, resolution) {
        var style = new ol.style.Style({
          stroke: new ol.style.Stroke({
            color: 'rgba(255, 0, 0, 0.6)',
            width: 2.0
          }),
          text: new ol.style.Text({
            text: feature.get('number'),
            font: 'bold 5mm sans-serif',
            fill: new ol.style.Fill({color: 'rgba(0, 0, 0, 1.0)'}),
            stroke: new ol.style.Stroke({color: 'rgba(255, 255, 255, 1.0)', width: 3.0})
          })
        });
        return [style];
      }
    });

    var streetsLayer = new ol.layer.Tile({
      source: new ol.source.OSM()
    });
    // high DPI spike
    streetsLayer = new ol.layer.Tile({
      source: new ol.source.XYZ({
        url: '//a.osm.rrze.fau.de/osmhd/{z}/{x}/{y}.png',
        tileSize: [512, 512]
      })
    });

    var map = new ol.Map({
      target: elementId,
      pixelRatio: 2, // render at high DPI for printing
      layers: [streetsLayer, regionLayer, territoryLayer],
      view: new ol.View({
        center: ol.proj.fromLonLat([0.0, 0.0]),
        zoom: 1,
        minResolution: 1.25, // prevent zooming too close, show more surrounding for small territories
        zoomFactor: 1.1 // zoom in small steps to enable fine tuning
      })
    });
    //useHighDpiMaps(map);
    map.getView().fit(
      regionLayer.getSource().getExtent(),
      map.getSize(),
      {
        padding: [5, 5, 5, 5],
        minResolution: 3.0
      }
    );
  }

  return {
    initTerritoryMap: initTerritoryMap,
    initTerritoryMiniMap: initTerritoryMiniMap,
    initNeighborhoodMap: initNeighborhoodMap,
    initRegionMap: initRegionMap
  };
})();
