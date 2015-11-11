var territoryBro = (function () {
  var MAX_ZOOM = 18;

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
      layers: [streetsLayer, territoryLayer],
      view: new ol.View({
        center: ol.proj.fromLonLat([0.0, 0.0]),
        zoom: 1
      })
    });
    var view = map.getView();
    view.fit(territorySource.getExtent(), map.getSize());
    view.setZoom(Math.min(MAX_ZOOM, view.getZoom()));
  }

  return {
    initTerritoryMap: initTerritoryMap
  };
})();
