// Copyright © 2015-2019 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import React from "react";
import LoginButton from "./LoginButton";
import LogoutButton from "./LogoutButton";
import DevLoginButton from "./DevLoginButton";
import {getSettings} from "../api";

let AuthenticationPanel = () => {
  const settings = getSettings();
  const dev = settings.dev;
  if (settings.user) {
    const fullName = settings.user.name;
    return <p>
      Logged in as {fullName} <LogoutButton/>
    </p>;
  } else {
    return <p>
      <LoginButton/> {dev && <DevLoginButton/>}
    </p>;
  }
};

export default AuthenticationPanel;
