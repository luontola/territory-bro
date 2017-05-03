// Copyright © 2015-2017 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import "../../css/territory-cards.css";
import React from "react";
import {initTerritoryMiniMap} from "../maps";

export default class TerritoryMiniMap extends React.Component {
  element: HTMLDivElement;

  componentDidMount() {
    const {territory, regions} = this.props;
    initTerritoryMiniMap(this.element, territory, regions);
  }

  render() {
    const {className} = this.props;
    return (
      <div className={className} ref={el => this.element = el}/>
    );
  }
}
