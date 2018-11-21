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
  congregationIds: Array<string>,
  supportEmail: string,
  loggedIn: boolean,
}

function QgisProjectSection({territoryCount, regionCount, congregationIds, supportEmail}) {
  return (
    <React.Fragment>
      <h2>QGIS Project</h2>

      <p>Your congregation's database has currently {territoryCount} territories and {regionCount} regions.</p>

      <p>To add and edit the territories in your database, first download and install <a href="https://qgis.org/">
        QGIS 3.4 (Long Term Release)</a>.</p>

      <p>Then download the QGIS project file for your congregation and open it in QGIS:</p>

      <ul>
        {congregationIds.map(congregationId =>
          <li key={congregationId}>
            <a href={`/api/download-qgis-project/${congregationId}`} rel="nofollow">
              {congregationId}-territories.qgs</a></li>
        )}
      </ul>

      <p>For more help, read the guides at <a
        href="https://territorybro.com/guide/">https://territorybro.com/guide/</a> or ask <a
        href={`mailto:${supportEmail}`}>{supportEmail}</a>.
      </p>
    </React.Fragment>
  );
}

function ImportSection({territoryCount, regionCount}) {
  return (
    <React.Fragment>
      <h2>Import</h2>

      <p>The database has currently {territoryCount} territories and {regionCount} regions.</p>

      <form action="/api/clear-database" method="post">
        <button type="submit" className="btn btn-primary">Delete All</button>
      </form>

      <form action="/api/import-territories" method="post" encType="multipart/form-data">
        <p>Territories GeoJSON: <input type="file" name="territories"/></p>
        <p>Regions GeoJSON: <input type="file" name="regions"/></p>
        <button type="submit" className="btn btn-primary">Import</button>
      </form>
    </React.Fragment>
  );
}

let OverviewPage = ({territoryCount, regionCount, congregationIds, supportEmail, loggedIn}: Props) => (
  <Layout>
    <h1>Territory Bro</h1>

    <p>Territory Bro is a tool for managing territory cards in the congregations of Jehovah's Witnesses.</p>

    <p>For more information, see <a href="http://territorybro.com">http://territorybro.com</a></p>

    {loggedIn && congregationIds.length > 0 ?
      <QgisProjectSection territoryCount={territoryCount}
                          regionCount={regionCount}
                          congregationIds={congregationIds}
                          supportEmail={supportEmail}/> :
      <ImportSection territoryCount={territoryCount}
                     regionCount={regionCount}/>
    }
  </Layout>
);

function mapStateToProps(state: State): Props {
  return {
    territoryCount: state.api.territories.length,
    regionCount: state.api.regions.length,
    congregationIds: state.api.congregations.map(c => c.id),
    supportEmail: state.api.supportEmail,
    loggedIn: state.api.authenticated,
  };
}

OverviewPage = connect(mapStateToProps)(OverviewPage);

export default OverviewPage;
