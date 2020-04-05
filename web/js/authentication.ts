// Copyright © 2015-2019 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import auth0 from "auth0-js";
import {loginWithIdToken} from "./api";

export function buildAuthenticator(auth0Domain: string, auth0ClientId: string) {
  const webAuth = new auth0.WebAuth({
    domain: auth0Domain,
    clientID: auth0ClientId,
    responseType: 'id_token',
    scope: 'openid email profile',
    redirectUri: `${window.location.origin}/login-callback?return=${window.location.pathname}`
  });

  function findAuthResult() {
    return new Promise((resolve, reject) => {
      webAuth.parseHash(function (err, authResult) {
        if (err) {
          console.warn("Authentication failed", err);
          reject(new Error("Authentication failed"));
        } else {
          console.info("Authentication succeeded", authResult);
          resolve(authResult);
        }
      });
    });
  }

  async function handleAuthentication() {
    const authResult = await findAuthResult();
    if (authResult && authResult.idToken) {
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