// Copyright © 2015-2019 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import React from "react";
import {getCongregations, getSettings} from "../api";
import {Link} from "@reach/router";

// TODO: move QGIS project download to congregation page
function QgisProjectSection({territoryCount, regionCount, congregationIds, supportEmail}) {
  return (
    <>
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
    </>
  );
}

const CongregationsList = () => {
  const congregations = getCongregations();
  return (
    <>
      <h2>Congregations</h2>
      {congregations.length === 0 ?
        <p>You have no congregations. Please <Link to={"/register"}>register</Link> one first.</p> :
        <ul>
          {congregations.map(cong => (
            <li key={cong.id}><Link to={`/congregation/${cong.id}`}>{cong.name}</Link></li>
          ))}
        </ul>
      }
    </>
  );
};

const OverviewPage = () => {
  const settings = getSettings();
  const loggedIn = settings.user.authenticated;
  return (
    <>
      <h1>Territory Bro</h1>

      <p>Territory Bro is a tool for managing territory cards in the congregations of Jehovah's Witnesses.</p>

      <p>For more information, see <a href="http://territorybro.com">http://territorybro.com</a></p>

      {loggedIn &&
      <CongregationsList/>}
    </>
  );
};

export default OverviewPage;
