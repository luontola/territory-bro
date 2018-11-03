// Copyright © 2015-2018 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import auth0 from "auth0-js";
import {loginWithIdToken} from "./api";

const webAuth = new auth0.WebAuth({
  domain: 'luontola.eu.auth0.com',
  clientID: '8tVkdfnw8ynZ6rXNndD6eZ6ErsHdIgPi',
  responseType: 'id_token',
  //responseType: 'token id_token',
  audience: "https://luontola.eu.auth0.com/userinfo",
  // TODO: Usage of scope 'openid profile' is not recommended. See https://auth0.com/docs/scopes for more details.
  scope: 'openid profile',
  redirectUri: 'http://localhost:8080/login-callback',
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

export async function handleAuthentication() {
  const authResult = await findAuthResult();
  //if (authResult && authResult.accessToken && authResult.idToken) {
  if (authResult && authResult.idToken) {
    await loginWithIdToken(authResult.idToken);
    // TODO: reload data, at least /api/my-congregations
  }
}

export function openLoginDialog() {
  webAuth.authorize();
}
