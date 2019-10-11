// Copyright © 2015-2019 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import React from "react";
import {getSettings} from "../api";
import {buildAuthenticator} from "../authentication";

const JoinPage = () => {
  const settings = getSettings();
  const loggedIn = settings.user.authenticated;
  const userId = settings.user.id;

  // TODO: deduplicate with registration page
  if (!loggedIn) {
    const {domain, clientId} = settings.auth0;
    const auth = buildAuthenticator(domain, clientId);
    auth.login();
    return <p>Please wait, you will be redirected...</p>;
  }

  return (
    <>
      <h1>Join an Existing Congregation</h1>

      <p>Ask the brother who is taking care of the territories in your congregation to give you Territory Bro
        access.</p>

      <p>You will need to tell him your User ID, which is: <div
        style={{fontSize: '150%', margin: '15px'}}>{userId}</div></p>
    </>
  );
};

export default JoinPage;
