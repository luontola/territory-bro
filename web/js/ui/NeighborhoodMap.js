// Copyright © 2015-2017 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import "../../css/territory-cards.css";
import React from "react";
import type {MapRaster} from "../maps";
import {initNeighborhoodMap} from "../maps";
import type {Territory} from "../api";

export default class NeighborhoodMap extends React.Component {
  props: {
    className?: string,
    territory: Territory,
    mapRaster: MapRaster,
  };
  element: HTMLDivElement;
  map: *;

  componentDidMount() {
    const {territory, mapRaster} = this.props;
    this.map = initNeighborhoodMap(this.element, territory);
    this.map.setStreetsLayerRaster(mapRaster);
  }

  componentDidUpdate() {
    const {mapRaster} = this.props;
    this.map.setStreetsLayerRaster(mapRaster);
  }

  render() {
    const {className} = this.props;
    return (
      <div style={{width: '100%', height: '100%'}} className={className} ref={el => this.element = el}/>
    );
  }
}
