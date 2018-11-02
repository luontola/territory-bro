// Copyright © 2015-2018 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import "ol/ol.css";
import React from "react";
import styles from "./OpenLayersMap.css";

export default class OpenLayersMap extends React.Component {
  element: HTMLDivElement;

  render() {
    return (
      <div className={styles.root} ref={el => this.element = el}/>
    );
  }
}
                                                                                                     