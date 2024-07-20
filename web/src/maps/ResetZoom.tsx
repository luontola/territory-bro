// Copyright © 2015-2024 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import Control from "ol/control/Control";
import i18n from "../i18n";
import minimizeSvg from '@fortawesome/fontawesome-free/svgs/solid/minimize.svg?raw';
import {parseFontAwesomeIcon} from "../font-awesome.ts";

class ResetZoom extends Control {
  constructor(resetZoom: (map: any, opts: {}) => void) {
    super({
      element: document.createElement('div')
    });

    const onClick = event => {
      event.preventDefault();
      resetZoom(super.getMap(), {duration: 500});
    }

    const button = document.createElement("button");
    button.type = "button";
    button.title = i18n.t('Map.resetZoom');
    button.addEventListener("click", onClick);
    button.appendChild(parseFontAwesomeIcon(minimizeSvg));

    const element = this.element;
    element.className = 'ol-zoom-extent ol-unselectable ol-control';
    element.appendChild(button);
  }
}

export default ResetZoom;
