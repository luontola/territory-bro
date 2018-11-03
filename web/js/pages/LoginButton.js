// Copyright © 2015-2018 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import React from "react";
import type {State} from "../reducers";
import connect from "react-redux/es/connect/connect";
import {openLoginDialog} from "../authentication";

type Props = {
  currentCongregationId: ?string,
};

// TODO: check if already logged in
let LoginButton = ({currentCongregationId}: Props) => (
  <React.Fragment>
    <button onClick={openLoginDialog}>Login</button>
  </React.Fragment>
);

function mapStateToProps(state: State) {
  return {
    currentCongregationId: state.config.congregationId,
  };
}

LoginButton = connect(mapStateToProps)(LoginButton);

export default LoginButton;
