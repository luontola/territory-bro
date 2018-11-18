// Copyright © 2015-2018 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import React from "react";
import {devLogin} from "../api";

async function handleClick() {
  await devLogin();
  document.location.reload();
}

const DevLoginButton = () => (
  <button type="button" onClick={handleClick}>Dev Login</button>
);

export default DevLoginButton;
