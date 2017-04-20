// Copyright © 2015-2017 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import "../../css/territory-cards.css";
import React from "react";
import {Layout} from "./Layout";
import {initNeighborhoodMap} from "../maps";
import type {Territory} from "../api";

class NeighborhoodMap extends React.Component {
  map: HTMLDivElement;

  componentDidMount() {
    const {territory} = this.props;
    initNeighborhoodMap(this.map, territory);
  }

  render() {
    return (
      <div className="croppable-territory-card">
        <div className="crop-mark-top-left"><img src="/img/crop-mark.svg" alt=""/></div>
        <div className="crop-mark-top-right"><img src="/img/crop-mark.svg" alt=""/></div>
        <div className="crop-area neighborhood-map" ref={el => this.map = el}/>
        <div className="crop-mark-bottom-left"><img src="/img/crop-mark.svg" alt=""/></div>
        <div className="crop-mark-bottom-right"><img src="/img/crop-mark.svg" alt=""/></div>
      </div>
    );
  }
}

const NeighborhoodMapsPage = ({territories}: { territories: Array<Territory> }) => (
  <Layout>
    <h1 className="no-print">Neighborhood Maps</h1>
    {territories.map(territory =>
      <NeighborhoodMap key={territory.id} territory={territory}/>
    )}
  </Layout>
);

export {NeighborhoodMapsPage};
