// Copyright © 2015-2023 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import LoginButton from "./LoginButton";
import LogoutButton from "./LogoutButton";
import DevLoginButton from "./DevLoginButton";
import {getSettings} from "../api";

let AuthenticationPanel = () => {
  const settings = getSettings();
  const dev = settings.dev;
  if (settings.user) {
    const fullName = settings.user.name;
    return <>
      Logged in as {fullName} <LogoutButton/>
    </>;
  } else {
    return <>
      <LoginButton/> {dev && <DevLoginButton/>}
    </>;
  }
};

export default AuthenticationPanel;
