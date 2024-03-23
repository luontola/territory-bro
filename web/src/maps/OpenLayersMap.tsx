// Copyright © 2015-2024 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import "ol/ol.css";
import React, {createRef, RefObject} from "react";
import styles from "./OpenLayersMap.module.css";
import {findMapRasterById, MapRaster, mapRasters} from "./mapOptions.ts";

type Props = {
  printout: boolean
};

export default class OpenLayersMap<P extends Props> extends React.Component<P> {

  elementRef: RefObject<HTMLDivElement>;

  constructor(props: P) {
    super(props);
    this.elementRef = createRef()
  }

  render() {
    const {printout} = this.props;
    let className = styles.root;
    if (printout) {
      className += " " + styles.printout;
    }
    return <div className={className} ref={this.elementRef}/>;
  }
}

export abstract class OpenLayersMapElement extends HTMLElement {
  map;

  constructor() {
    super();
  }

  connectedCallback() {
    const mapRaster = findMapRasterById(this.getAttribute("map-raster") ?? mapRasters[0].id) ?? mapRasters[0];
    const printout = !!this.getAttribute("printout");

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
      this.map = this.createMap({root, mapRaster, printout})
    }, 20);
  }

  abstract createMap(opts: { root: HTMLDivElement, mapRaster: MapRaster, printout: boolean });

  disconnectedCallback() {
    this.map?.unmount();
  }
}
