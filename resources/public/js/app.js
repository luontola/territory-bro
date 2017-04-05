var territoryBro = (function () {

  function wktToFeature(wkt) {
    var feature = new ol.format.WKT().readFeature(wkt);
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
    var attribution = new ol.control.Attribution({
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

  function territoryTextStyle(territoryNumber, fontSize) {
    return new ol.style.Text({
      text: territoryNumber,
      font: 'bold ' + fontSize + ' sans-serif',
      fill: new ol.style.Fill({color: 'rgba(0, 0, 0, 1.0)'}),
      stroke: new ol.style.Stroke({color: 'rgba(255, 255, 255, 1.0)', width: 3.0})
    });
  }

  // map constructors

  function initTerritoryMap(elementId, territoryWkt) {
    var territoryLayer = new ol.layer.Vector({
      source: new ol.source.Vector({
        features: [wktToFeature(territoryWkt)]
      }),
      style: new ol.style.Style({
        stroke: territoryStrokeStyle(),
        fill: territoryFillStyle()
      })
    });

    var map = new ol.Map({
      target: elementId,
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

  function initTerritoryMiniMap(elementId, territoryWkt, viewportWkt, congregationWkt, subregionsWkt) {
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

    var viewportSource = new ol.source.Vector({
      features: [wktToFeature(viewportWkt)]
    });

    var congregationLayer = new ol.layer.Vector({
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

    var subregionsLayer = new ol.layer.Vector({
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

    var map = new ol.Map({
      target: elementId,
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

  function initNeighborhoodMap(elementId, territoryNumber, territoryWkt) {
    var territoryLayer = new ol.layer.Vector({
      source: new ol.source.Vector({
        features: [wktToFeature(territoryWkt)]
      }),
      style: new ol.style.Style({
        stroke: territoryStrokeStyle(),
        fill: territoryFillStyle(),
        text: territoryTextStyle(territoryNumber, '180%')
      })
    });

    var map = new ol.Map({
      target: elementId,
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
          stroke: territoryStrokeStyle(),
          text: territoryTextStyle(feature.get('number'), '5mm')
        });
        return [style];
      }
    });

    var map = new ol.Map({
      target: elementId,
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

  return {
    initTerritoryMap: initTerritoryMap,
    initTerritoryMiniMap: initTerritoryMiniMap,
    initNeighborhoodMap: initNeighborhoodMap,
    initRegionMap: initRegionMap
  };
})();
