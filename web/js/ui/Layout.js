// Copyright © 2015-2017 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import "purecss/build/pure-min.css";
import "../../css/layout.css";
import type {Children} from "react";
import React from "react";
import Link from "./Link";
import {changeLanguage} from "../intl";

type Props = { children?: Children }

const Layout = ({children}: Props) => (
  <div id="layout">

    <nav className="no-print">
      <ul>
        <li><Link to="/">Overview</Link></li>
        <li><Link to="/territory-cards">Territory Cards</Link></li>
        <li><Link to="/neighborhood-maps">Neighborhood Maps</Link></li>
        <li><Link to="/region-maps">Region Maps</Link></li>
      </ul>
      <p>Change language:
        {' '}<a href="#" onClick={() => changeLanguage('en')}>English</a>
        {' '}<a href="#" onClick={() => changeLanguage('fi')}>Finnish</a>
        {' '}<a href="#" onClick={() => changeLanguage('pt')}>Portuguese</a>
      </p>
    </nav>

    <div className="container">
      {children}
    </div>

  </div>
);

export default Layout;
