// Copyright © 2015-2018 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import React from "react";
import type {State} from "../reducers";
import connect from "react-redux/es/connect/connect";
import LoginButton from "./LoginButton";

type Props = {
  loggedIn: boolean,
  name: ?string,
};

let AuthenticationPanel = ({loggedIn, name}: Props) => {
  if (loggedIn) {
    return (
      <p>
        Logged in as {name}
      </p>
    );
  } else {
    return (
      <p>
        <LoginButton/>
      </p>
    );
  }
};

function mapStateToProps(state: State) {
  return {
    loggedIn: state.api.congregations.length > 0,
    name: "X", // TODO: get the name from server
  };
}

AuthenticationPanel = connect(mapStateToProps)(AuthenticationPanel);

export default AuthenticationPanel;
