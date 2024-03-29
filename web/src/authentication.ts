// Copyright © 2015-2024 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import auth0, {Auth0DecodedHash} from "auth0-js";
import {loginWithIdToken, Settings} from "./api";

export function auth0Authenticator(settings: Settings) {
  const returnToUrl = `${window.location.pathname}${window.location.search}`
  const webAuth = new auth0.WebAuth({
    domain: settings.auth0.domain,
    clientID: settings.auth0.clientId,
    responseType: 'id_token',
    scope: 'openid email profile',
    redirectUri: `${window.location.origin}/login-callback?return=${encodeURIComponent(returnToUrl)}`
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
    webAuth.authorize({prompt: "select_account"});
  }

  function logout() {
    webAuth.logout({returnTo: `${window.location.origin}/`});
  }

  return {
    handleAuthentication,
    login,
    logout,
  };
}
