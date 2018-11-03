// Copyright © 2015-2018 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import React from "react";
import {openLoginDialog} from "../authentication";

const LoginButton = () => (
  <React.Fragment>
    <button onClick={openLoginDialog}>Login</button>
  </React.Fragment>
);

export default LoginButton;
