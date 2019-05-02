// Copyright © 2015-2019 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import "purecss/build/pure-min.css";
import "purecss/build/grids-responsive-min.css";
import "./Layout.css";
import * as React from 'react';
import LanguageSelection from "./LanguageSelection";
import CongregationSelection from "./CongregationSelection";
import AuthenticationPanel from "./AuthenticationPanel";
import {getSettings} from "../api";
import {Link} from "@reach/router";

type Props = {
  title?: string,
  children?: React.Node,
}

class Layout extends React.Component<Props> {
  componentDidMount() {
    const {title} = this.props;
    const site = "Territory Bro";
    document.title = (title ? `${title} - ${site}` : site);
  }

  render() {
    const settings = getSettings();
    const loggedIn = settings.user.authenticated;
    const {children} = this.props;
    return (
      <div id="layout">

        <nav className="no-print">
          <AuthenticationPanel/>
          <CongregationSelection/>
          <ul>
            <li><Link to="/">Overview</Link></li>
            {loggedIn &&
            <>
              <li><Link to="/territory-cards">Territory Cards</Link></li>
              <li><Link to="/neighborhood-maps">Neighborhood Maps</Link></li>
              <li><Link to="/rural-territory-cards">Rural Territory Cards</Link></li>
              <li><Link to="/region-maps">Region Maps</Link></li>
            </>
            }
          </ul>
          {loggedIn &&
          <LanguageSelection/>
          }
        </nav>

        <div className="container">
          {children}
        </div>

      </div>
    );
  }
}

export default Layout;
