// Copyright © 2015-2017 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import "../../css/territory-cards.css";
import React from "react";
import {initTerritoryMap} from "../maps";

export default class TerritoryMap extends React.Component {
  element: HTMLDivElement;
  map: *;

  componentDidMount() {
    const {territory, mapRaster} = this.props;
    this.map = initTerritoryMap(this.element, territory);
    this.map.setStreetsLayerRaster(mapRaster);
  }

  componentDidUpdate() {
    const {mapRaster} = this.props;
    this.map.setStreetsLayerRaster(mapRaster);
  }

  render() {
    const {className} = this.props;
    return (
      <div className={className} ref={el => this.element = el}/>
    );
  }
}
