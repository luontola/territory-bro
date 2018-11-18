// Copyright © 2015-2018 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import React from "react";
import type {State} from "../reducers";
import connect from "react-redux/es/connect/connect";
import LoginButton from "./LoginButton";
import LogoutButton from "./LogoutButton";
import RegisterButton from "./RegisterButton";
import DevLoginButton from "./DevLoginButton";

type Props = {
  dev: boolean,
  loggedIn: boolean,
  fullName: ?string,
};

let AuthenticationPanel = ({dev, loggedIn, fullName}: Props) => {
  if (loggedIn) {
    return (
      <p>
        Logged in as {fullName} <LogoutButton/> <RegisterButton/>
      </p>
    );
  } else {
    return (
      <p>
        <LoginButton/> {dev && <DevLoginButton/>} <RegisterButton/>
      </p>
    );
  }
};

function mapStateToProps(state: State) {
  return {
    dev: state.api.dev,
    loggedIn: state.api.authenticated,
    fullName: state.api.userFullName,
  };
}

AuthenticationPanel = connect(mapStateToProps)(AuthenticationPanel);

export default AuthenticationPanel;
