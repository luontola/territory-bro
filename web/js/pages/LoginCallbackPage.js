// Copyright © 2015-2019 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import React, {useEffect} from "react";
import {getSettings} from "../api";
import {buildAuthenticator} from "../authentication";
import history from "../history";

const LoginCallbackPage = () => {
  const settings = getSettings();
  const {domain, clientId} = settings.auth0;
  const auth = buildAuthenticator(domain, clientId);

  useEffect(() => {
    (async () => {
      await auth.handleAuthentication();
      const params = new URLSearchParams(document.location.search.substring(1));
      history.replace({pathname: params.get('return') || '/'});
    })();
  });

  return <p>Logging you in...</p>;
};

export default LoginCallbackPage;
