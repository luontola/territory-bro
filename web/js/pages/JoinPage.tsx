// Copyright © 2015-2019 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import React from "react";
import {getSettings} from "../api";
import {buildAuthenticator} from "../authentication";
import ClipboardJS from "clipboard";

new ClipboardJS('#copy-your-user-id');

const JoinPage = () => {
  const settings = getSettings();
  // TODO: deduplicate with registration page
  if (!settings.user) {
    const {
      domain,
      clientId
    } = settings.auth0;
    const auth = buildAuthenticator(domain, clientId);
    auth.login();
    return <p>Please wait, you will be redirected...</p>;
  }
  const userId = settings.user.id;

  return <>
    <h1>Join an Existing Congregation</h1>

    <p>To access a congregation in Territory Bro, you will need to tell your <em>User ID</em> to the brother who is
      taking care of the territories in your congregation, so that he can give you access.</p>

    <p>Your User ID is:</p>

    <p id="your-user-id" style={{fontSize: '150%', margin: '15px'}}>{userId}</p>

    <p>
      <button id="copy-your-user-id" type="button" className="pure-button" data-clipboard-target="#your-user-id">
        Copy to clipboard
      </button>
    </p>
  </>;
};

export default JoinPage;
