// Copyright © 2015-2019 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0


import React from "react";
import {devLogin} from "../api";

async function handleClick() {
  await devLogin();
  document.location.reload();
}

const DevLoginButton = () => <button type="button" className="pure-button" onClick={handleClick}>Dev Login</button>;

export default DevLoginButton;