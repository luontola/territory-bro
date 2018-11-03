// Copyright © 2015-2018 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import React from "react";
import type {State} from "../reducers";
import connect from "react-redux/es/connect/connect";
import type {Congregation} from "../api";
import {changeCongregation} from "../congregation";

type Props = {
  currentCongregationId: ?string,
  congregations: Array<Congregation>,
};

let CongregationSelection = ({currentCongregationId, congregations}: Props) => (
  <React.Fragment>
    <select onChange={handleCongregationChange} value={currentCongregationId}>
      {congregations.map(({id, name}) =>
        <option key={id} value={id}>{name}</option>)}
    </select>
  </React.Fragment>
);

function handleCongregationChange(event) {
  event.preventDefault();
  changeCongregation(event.target.value)
}

function mapStateToProps(state: State) {
  return {
    currentCongregationId: state.config.congregationId,
    congregations: state.api.congregations,
  };
}

CongregationSelection = connect(mapStateToProps)(CongregationSelection);

export default CongregationSelection;
