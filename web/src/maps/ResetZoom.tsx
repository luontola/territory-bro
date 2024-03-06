// Copyright © 2015-2024 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import Control from "ol/control/Control";
import {FontAwesomeIcon} from '@fortawesome/react-fontawesome';
import {faMinimize} from '@fortawesome/free-solid-svg-icons';
import {createRoot} from "react-dom/client";
import i18n from "../i18n";

class ResetZoom extends Control {
  constructor(resetZoom: (map: any, opts: {}) => void) {
    super({
      element: document.createElement('div')
    });

    const onClick = event => {
      event.preventDefault();
      resetZoom(super.getMap(), {duration: 500});
    }

    const element = this.element;
    element.className = 'ol-zoom-extent ol-unselectable ol-control';

    createRoot(element).render(
      <button type="button" title={i18n.t('Map.resetZoom')} onClick={onClick}>
        <FontAwesomeIcon icon={faMinimize} style={{verticalAlign: "text-bottom"}}/>
      </button>
    );
  }
}

export default ResetZoom;
