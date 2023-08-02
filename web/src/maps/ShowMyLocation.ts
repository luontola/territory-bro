// Copyright © 2015-2020 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import Control from "ol/control/Control";
import {boundingExtent, buffer, containsCoordinate, extend, extendCoordinate, getHeight, getWidth} from "ol/extent";

class ShowMyLocation extends Control {
  constructor(startGeolocation: (map: any) => void) {
    super({
      element: document.createElement('div')
    });

    const icon = document.createElement('i');
    icon.setAttribute('class', 'fas fa-crosshairs');
    // icon.setAttribute('class', 'fas fa-location-arrow');
    // icon.setAttribute('class', 'fas fa-street-view');

    const button = document.createElement('button');
    button.setAttribute('type', 'button');
    button.title = "Show my location";
    button.appendChild(icon);

    let geolocation = null;
    const onClick = event => {
      event.preventDefault();
      const map = super.getMap();

      // Start the geolocation tracking only once, but always zoom out to fit the user
      if (!geolocation) {
        geolocation = startGeolocation(map);
      }

      function zoomOutToFit(position) {
        const view = map.getView();
        const extent = view.calculateExtent()

        // TODO: calculate distance to nearest edge, and zoom out if too close to it
        const paddedPosition1 = buffer(boundingExtent([position]), 30);
        if (containsCoordinate(extent, paddedPosition1)) {
          return; // point is already clearly inside; no need to zoom
        }

        // Pad the user position relative to the map size, to avoid
        // it being too close to the edge where it's hard to notice.
        extendCoordinate(extent, position);
        // TODO: calculate distance to nearest edge, instead of assuming the map is square
        const dimension = Math.min(getWidth(extent), getHeight(extent));
        const paddedPosition2 = buffer(boundingExtent([position]), dimension * 0.5);
        extend(extent, paddedPosition2);

        // TODO: could also just use this padding here, instead of the above extent calculations,
        //       but in that case the extent would need to be calculated from the territory layer,
        //       so that the extent calculation is idempotent and re-clicking does not zoom further out
        view.fit(extent, {
          duration: 500,
          padding: [20, 20, 20, 20], // same padding as in TerritoryMap's resetZoom for consistency
        });
      }

      // On first click the position data is not yet available; need asynchrony.
      // On subsequent clicks the position is cached and can be used synchronously.
      const position = geolocation.getPosition();
      if (position) {
        zoomOutToFit(position);
      } else {
        geolocation.once('change:position', () => {
          zoomOutToFit(geolocation.getPosition());
        })
      }
    };
    button.addEventListener('click', onClick, false);

    const element = this.element;
    element.className = 'ol-unselectable ol-control';
    element.style = 'top: 7.15em; left: .5em;'
    element.appendChild(button);
  }
}

export default ShowMyLocation;
