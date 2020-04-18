// Copyright © 2015-2020 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import Control from "ol/control/Control";

class ResetZoom extends Control {
  constructor(resetZoom: (map: any) => void) {
    super({
      element: document.createElement('div')
    });

    const button = document.createElement('button');
    button.setAttribute('type', 'button');
    button.title = "Reset zoom";
    button.appendChild(document.createTextNode("🔄"));

    const onClick = event => {
      event.preventDefault();
      resetZoom(super.getMap());
    };
    button.addEventListener('click', onClick, false);

    const element = this.element;
    element.className = 'ol-zoom-extent ol-unselectable ol-control';
    element.appendChild(button);
  }
}

export default ResetZoom;
