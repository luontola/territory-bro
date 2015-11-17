var territoryBro = (function () {
  function initTerritoryMap(elementId, areaWkt) {
    var territoryArea = new ol.format.WKT().readFeature(areaWkt);
    territoryArea.getGeometry().transform('EPSG:4326', 'EPSG:3857');
    var territorySource = new ol.source.Vector({
      features: [territoryArea]
    });
    var territoryStyles = [
      new ol.style.Style({
        stroke: new ol.style.Stroke({
          color: 'rgba(255, 0, 0, 1.0)',
          width: 3
        }),
        fill: new ol.style.Fill({
          color: 'rgba(255, 0, 0, 0.1)'
        })
      })
    ];
    var territoryLayer = new ol.layer.Vector({
      source: territorySource,
      style: territoryStyles
    });

    var streetsLayer = new ol.layer.Tile({
      source: new ol.source.OSM()
    });

    var map = new ol.Map({
      target: elementId,
      pixelRatio: 2, // render at high DPI for printing
      layers: [streetsLayer, territoryLayer],
      view: new ol.View({
        center: ol.proj.fromLonLat([0.0, 0.0]),
        zoom: 1,
        // show more surrounding area for small territories
        maxZoom: 18
      })
    });

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

    // zoom and center on the territory
    map.getView().fit(territorySource.getExtent(), map.getSize());
  }

  return {
    initTerritoryMap: initTerritoryMap
  };
})();
