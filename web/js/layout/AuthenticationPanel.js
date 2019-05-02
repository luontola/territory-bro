// Copyright © 2015-2019 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import React from "react";
import LoginButton from "./LoginButton";
import LogoutButton from "./LogoutButton";
import RegisterButton from "./RegisterButton";
import DevLoginButton from "./DevLoginButton";
import {useSettings} from "../api";

let AuthenticationPanel = () => {
  const settings = useSettings();
  const dev = settings.dev;
  const loggedIn = settings.user.authenticated;
  const fullName = settings.user.name;

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

export default AuthenticationPanel;
