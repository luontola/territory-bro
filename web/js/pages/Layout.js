// Copyright © 2015-2018 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import "purecss/build/pure-min.css";
import "purecss/build/grids-responsive-min.css";
import "./Layout.css";
import type {Children} from "react";
import React from "react";
import Link from "./Link";
import {changeLanguage} from "../intl";

class Layout extends React.Component {
  props: {
    title?: string,
    children?: Children,
  };

  componentDidMount() {
    const {title} = this.props;
    const site = "Territory Bro";
    document.title = (title ? `${title} - ${site}` : site);
  }

  render() {
    const {children} = this.props;
    return (
      <div id="layout">

        <nav className="no-print">
          <ul>
            <li><Link href="/">Overview</Link></li>
            <li><Link href="/territory-cards">Territory Cards</Link></li>
            <li><Link href="/neighborhood-maps">Neighborhood Maps</Link></li>
            <li><Link href="/rural-territory-cards">Rural Territory Cards</Link></li>
            <li><Link href="/region-maps">Region Maps</Link></li>
          </ul>
          <p>Change language:
            {' '}<a href="#" onClick={handleLanguageChange('en')}>English</a>
            {' '}<a href="#" onClick={handleLanguageChange('fi')}>Finnish</a>
            {' '}<a href="#" onClick={handleLanguageChange('pt')}>Portuguese</a>
            {' '}<a href="#" onClick={handleLanguageChange('es')}>Spanish</a>
          </p>
        </nav>

        <div className="container">
          {children}
        </div>

      </div>
    );
  }
}

function handleLanguageChange(lang) {
  return (event) => {
    event.preventDefault();
    changeLanguage(lang);
  }
}

export default Layout;
