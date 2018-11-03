// Copyright © 2015-2018 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import React from "react";
import {buildAuthenticator} from "../authentication";
import type {State} from "../reducers";
import connect from "react-redux/es/connect/connect";

type Props = {
  auth0Domain: string,
  auth0ClientId: string,
}

let LoginButton = ({auth0Domain, auth0ClientId}: Props) => (
  <button type="button" onClick={() => {
    const auth = buildAuthenticator(auth0Domain, auth0ClientId);
    auth.login();
  }}>Login</button>
);

function mapStateToProps(state: State) {
  return {
    auth0Domain: state.api.auth0Domain,
    auth0ClientId: state.api.auth0ClientId,
  };
}

LoginButton = connect(mapStateToProps)(LoginButton);

export default LoginButton;
