// Copyright © 2015-2018 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import React from "react";
import history from "../history";

function handleClick() {
  history.push({
    pathname: '/register',
  });
}

let RegisterButton = () => (
  <button type="button" onClick={handleClick}>Register</button>
);

export default RegisterButton;
