// Copyright © 2015-2018 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import "ol/ol.css";
import React from "react";
import styles from "./OpenLayersMap.css";

type Props = {};

export default class OpenLayersMap<Props> extends React.Component<Props> {

  element: HTMLDivElement;

  render() {
    return <div className={styles.root} ref={el => {
      if (el) {
        this.element = el;
      }
    }}/>;
  }
}
