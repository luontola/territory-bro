// Copyright © 2015-2023 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import {logout} from "../api";

const LogoutButton = () => <button type="button" className="pure-button" onClick={logout}>Logout</button>;

export default LogoutButton;
