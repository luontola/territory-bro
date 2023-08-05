// Copyright © 2015-2023 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import auth0, {Auth0DecodedHash} from "auth0-js";
import {loginWithIdToken, Settings} from "./api";

export function auth0Authenticator(settings: Settings) {
  const webAuth = new auth0.WebAuth({
    domain: settings.auth0.domain,
    clientID: settings.auth0.clientId,
    responseType: 'id_token',
    scope: 'openid email profile',
    redirectUri: `${window.location.origin}/login-callback?return=${window.location.pathname}`
  });

  function findAuthResult(): Promise<Auth0DecodedHash> {
    return new Promise((resolve, reject) => {
      webAuth.parseHash(function (err, authResult) {
        if (err) {
          // XXX: with React StrictMode, this function is called twice and the second time the err is
          //      {error: 'invalid_token', errorDescription: '`state` does not match.'}
          console.warn("Authentication failed", err);
          reject(err);
        } else {
          console.info("Authentication succeeded", authResult);
          resolve(authResult);
        }
      });
    });
  }

  async function handleAuthentication() {
    const authResult = await findAuthResult();
    if (authResult?.idToken) {
      await loginWithIdToken(authResult.idToken);
    }
  }

  function login() {
    webAuth.authorize();
  }

  return {
    handleAuthentication,
    login
  };
}
