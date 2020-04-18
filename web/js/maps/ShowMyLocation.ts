// Copyright © 2015-2020 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import Control from "ol/control/Control";
import {buffer, extend} from "ol/extent";

class ShowMyLocation extends Control {
  constructor(startGeolocation: (map: any) => void) {
    super({
      element: document.createElement('div')
    });

    const button = document.createElement('button');
    button.setAttribute('type', 'button');
    button.title = "Show my location";
    button.appendChild(document.createTextNode("👀"));

    let geolocation = null;
    const onClick = event => {
      event.preventDefault();
      const map = super.getMap();

      // Start the geolocation tracking only once,
      // but on every click zoom out to fit the user
      if (!geolocation) {
        geolocation = startGeolocation(map);
      }

      function zoomOutToFit(geom) {
        const view = map.getView();
        view.fit(extend(view.calculateExtent(), buffer(geom.getExtent(), 20)));
      }

      // On first click we must wait asynchronously until
      // the sensors return some geolocation data.
      // On subsequent clicks we can use the latest known location.
      const geom = geolocation.getAccuracyGeometry();
      if (geom) {
        zoomOutToFit(geom);
      } else {
        geolocation.once('change:accuracyGeometry', () => {
          zoomOutToFit(geolocation.getAccuracyGeometry());
        })
      }
    };
    button.addEventListener('click', onClick, false);

    const element = this.element;
    element.className = 'ol-unselectable ol-control';
    element.style = 'bottom: .5em; left: .5em;'
    element.appendChild(button);
  }
}

export default ShowMyLocation;
