// Copyright © 2015-2018 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import auth0 from "auth0-js";

const webAuth = new auth0.WebAuth({
  domain: 'luontola.eu.auth0.com',
  clientID: '8tVkdfnw8ynZ6rXNndD6eZ6ErsHdIgPi',
  responseType: 'token id_token',
  audience: "https://luontola.eu.auth0.com/userinfo",
  scope: 'openid',
  redirectUri: 'http://localhost:8080/login-callback',
});

function getAuthResult() {
  return new Promise((resolve, reject) => {
    webAuth.parseHash(function (err, authResult) {
      console.log("auth result", err, authResult);
      if (err) {
        reject(err);
      } else {
        resolve(authResult);
      }
    });
  });
}

export async function handleAuthentication() {
  const authResult = await getAuthResult();
  if (authResult && authResult.accessToken && authResult.idToken) {
    console.log("Login success", authResult);
    // TODO: create session
    // headers: { 'Authorization': 'Bearer ' + idToken }
  }
}

export function openLoginDialog() {
  webAuth.authorize();
}
