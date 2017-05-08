// Copyright © 2015-2017 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import React from "react";
import styles from "./OpenLayersMap.css";

export default class OpenLayersMap extends React.Component {
  element: HTMLDivElement;

  render() {
    const {className} = this.props;
    // TODO: get rid of the className parameter to avoid precedence clashes
    return (
      <div className={styles.root + ' ' + className} ref={el => this.element = el}/>
    );
  }
}
                                                                                                     