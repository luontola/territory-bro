// Copyright © 2015-2024 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import {devLogin} from "../api";

const DevLoginButton = () => <button id="dev-login-button" type="button" className="pure-button" onClick={devLogin}>Dev
  Login</button>;

export default DevLoginButton;
