// Copyright © 2015-2019 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import React from "react";
import {buildAuthenticator} from "../authentication";
import {useSettings} from "../api";

let LoginButton = () => {
  const settings = useSettings();
  const {domain, clientId} = settings.auth0;
  return (
    <button type="button" onClick={() => {
      const auth = buildAuthenticator(domain, clientId);
      auth.login();
    }}>Login</button>
  );
};

export default LoginButton;
