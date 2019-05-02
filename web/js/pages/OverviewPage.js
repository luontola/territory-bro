// Copyright © 2015-2019 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import React from "react";
import Layout from "../layout/Layout";
import type {State} from "../reducers";
import {connect} from "react-redux";
import {useCongregations} from "../api";
import Link from "../layout/Link";

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

const CongregationsList = () => {
  const congregations = useCongregations();
  return (
    <>
      <h2>Congregations</h2>
      {congregations.length === 0 ?
        <p>You have no congregations. Please <Link href={"/register"}>register</Link> one first.</p> :
        <ul>
          {congregations.map(cong => (
            <li key={cong.id}><Link href={`/congregation/${cong.id}`}>{cong.name}</Link></li>
          ))}
        </ul>
      }
    </>
  );
};

let OverviewPage = ({territoryCount, regionCount, congregationIds, supportEmail, loggedIn}: Props) => (
  <Layout>
    <h1>Territory Bro</h1>

    <p>Territory Bro is a tool for managing territory cards in the congregations of Jehovah's Witnesses.</p>

    <p>For more information, see <a href="http://territorybro.com">http://territorybro.com</a></p>

    {loggedIn &&
    <CongregationsList/>}

    {loggedIn && congregationIds.length > 0 &&
    <QgisProjectSection territoryCount={territoryCount}
                        regionCount={regionCount}
                        congregationIds={congregationIds}
                        supportEmail={supportEmail}/>
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
