// Copyright © 2015-2018 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import React from "react";
import type {State} from "../reducers";
import connect from "react-redux/es/connect/connect";
import LoginButton from "./LoginButton";
import LogoutButton from "./LogoutButton";

type Props = {
  loggedIn: boolean,
  fullName: ?string,
};

let AuthenticationPanel = ({loggedIn, fullName}: Props) => {
  if (loggedIn) {
    return (
      <p>
        Logged in as {fullName} <LogoutButton/>
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
    loggedIn: state.api.authenticated,
    fullName: state.api.userFullName,
  };
}

AuthenticationPanel = connect(mapStateToProps)(AuthenticationPanel);

export default AuthenticationPanel;
