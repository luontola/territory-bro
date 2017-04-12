// Copyright © 2015-2017 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import "purecss/build/pure-min.css";
import "../../css/layout.css";
import React from "react";
import {Link} from "./Link";

const Layout = ({children}) => (
  <div id="layout">

    <nav className="no-print">
      <ul>
        <li><Link to="/">Overview</Link></li>
        <li><Link to="/territory-cards">Territory Cards</Link></li>
        <li><Link to="/neighborhood-maps">Neighborhood Maps</Link></li>
        <li><Link to="/region-maps">Region Maps</Link></li>
      </ul>
      <p>Change language:
        {' '}<a href="#">English</a>
        {' '}<a href="#">Finnish</a>
        {' '}<a href="#">Portuguese</a>
      </p>
    </nav>

    <div className="container">
      {children}
    </div>

  </div>
);

export {Layout};
