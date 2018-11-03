// Copyright © 2015-2018 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import React from "react";
import Layout from "../layout/Layout";
import type {State} from "../reducers";
import {connect} from "react-redux";

type Props = {
  territoryCount: number,
  regionCount: number,
  loggedIn: boolean,
}

let OverviewPage = ({territoryCount, regionCount, loggedIn}: Props) => (
  <Layout>
    <h1>Territory Bro</h1>

    <p>Territory Bro is a tool for managing territory cards in the congregations of Jehovah's Witnesses.</p>

    <p>For more information, see <a href="http://territorybro.com">http://territorybro.com</a></p>

    <h2>System Requirements</h2>

    <p>The territory card UI uses <a href="http://caniuse.com/#feat=css-variables">CSS varibles</a> and <a
      href="http://caniuse.com/#feat=css-grid">Grid layout</a>, which might not be supported by all web browsers.
      Use the latest version of Chrome or Firefox for best results. There are also differences in the image quality
      of PDFs, so compare various web browsers before printing your territory cards.</p>

    <h2>Import</h2>

    <p>The database has currently {territoryCount} territories and {regionCount} regions.</p>

    {loggedIn ?
      <p>Importing is disabled.</p>
      :
      <React.Fragment>
        <form action="/api/clear-database" method="post">
          <button type="submit" className="btn btn-primary">Delete All</button>
        </form>

        <form action="/api/import-territories" method="post" encType="multipart/form-data">
          <p>Territories GeoJSON: <input type="file" name="territories"/></p>
          <p>Regions GeoJSON: <input type="file" name="regions"/></p>
          <button type="submit" className="btn btn-primary">Import</button>
        </form>
      </React.Fragment>
    }
  </Layout>
);

function mapStateToProps(state: State): Props {
  return {
    territoryCount: state.api.territories.length,
    regionCount: state.api.regions.length,
    loggedIn: state.api.authenticated,
  };
}

OverviewPage = connect(mapStateToProps)(OverviewPage);

export default OverviewPage;
