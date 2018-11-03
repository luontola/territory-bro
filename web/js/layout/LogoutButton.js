// Copyright © 2015-2018 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import React from "react";
import {logout} from "../api";

const LogoutButton = () => (
  <button type="button" onClick={handleClick}>Logout</button>
);

async function handleClick() {
  await logout();
  window.location.reload();
}

export default LogoutButton;
