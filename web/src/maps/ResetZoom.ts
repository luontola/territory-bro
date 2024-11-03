import Control from "ol/control/Control";
import i18n from "../i18n.ts";
import resetZoomSvg from "@fortawesome/fontawesome-free/svgs/solid/minimize.svg?raw";
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

    const icon = parseFontAwesomeIcon(resetZoomSvg);
    icon.style.verticalAlign = "text-bottom";

    const button = document.createElement("button");
    button.type = "button";
    button.title = i18n.t('Map.resetZoom');
    button.addEventListener("click", onClick);
    button.appendChild(icon);

    const element = this.element;
    element.className = 'ol-zoom-extent ol-unselectable ol-control';
    element.appendChild(button);
  }
}

export default ResetZoom;
