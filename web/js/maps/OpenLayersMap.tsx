// Copyright © 2015-2020 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import "ol/ol.css";
import React from "react";
import styles from "./OpenLayersMap.css";

type Props = {
  printout: boolean
};

export default class OpenLayersMap<P extends Props> extends React.Component<P> {

  element: HTMLDivElement;

  render() {
    const {printout} = this.props;
    let className = styles.root;
    if (printout) {
      className += " " + styles.printout;
    }
    const setElement = el => {
      if (el) {
        this.element = el;
      }
    };
    return <div className={className} ref={setElement}/>;
  }
}
