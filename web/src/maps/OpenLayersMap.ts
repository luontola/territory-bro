import styles from "./OpenLayersMap.module.css";
import {findMapRasterById, MapRaster, mapRasters} from "./mapOptions.ts";

export abstract class OpenLayersMapElement extends HTMLElement {
  map?: { unmount(): void };

  connectedCallback() {
    const mapRaster = findMapRasterById(this.getAttribute("map-raster") ?? mapRasters[0].id) ?? mapRasters[0];
    const printout = this.getAttribute("printout") !== null;
    const settingsKey = this.getAttribute("settings-key");

    let className = styles.root;
    if (printout) {
      className += " " + styles.printout;
    }
    const root = document.createElement("div");
    root.setAttribute("class", className)
    this.appendChild(root)

    // If we instantiate the map immediately after creating the div, then resetZoom
    // sometimes fails to fit the map to the visible area. Waiting a short while
    // avoids that. It's not known if there is some event we could await instead.
    // Maybe the browser's layout engine hasn't yet determined the div's size?
    setTimeout(() => {
      this.map = this.createMap({root, mapRaster, printout, settingsKey})
    }, 20);
  }

  abstract createMap(opts: {
    root: HTMLDivElement,
    mapRaster: MapRaster,
    printout: boolean,
    settingsKey: string | null
  }): { unmount(): void } | undefined;

  disconnectedCallback() {
    this.map?.unmount();
  }
}
