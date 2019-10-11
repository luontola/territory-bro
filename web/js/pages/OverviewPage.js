// Copyright © 2015-2019 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import React from "react";
import {getCongregations, getSettings} from "../api";
import {Link} from "@reach/router";
import LoginButton from "../layout/LoginButton";

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

const RegisterButton = () => <Link to="/register" className="pure-button">Register a New Congregation</Link>;
const JoinButton = () => <Link to="/join" className="pure-button">Join an Existing Congregation</Link>;

const OverviewPage = () => {
  const settings = getSettings();
  const congregations = settings.user ? getCongregations() : [];
  return (
    <>
      <h1>Territory Bro</h1>

      <p>Territory Bro is a tool for managing territory cards in the congregations of Jehovah's Witnesses.
        See <a href="https://territorybro.com">territorybro.com</a> for more information.</p>

      {congregations.length > 0 &&
      <>
        <h2>Your Congregations</h2>
        <ul>
          {congregations.map(cong => (
            <li key={cong.id} style={{fontSize: '150%'}}><Link to={`/congregation/${cong.id}`}>{cong.name}</Link></li>
          ))}
        </ul>
        <p style={{paddingTop: '1.5em'}}>
          <RegisterButton/> <JoinButton/>
        </p>
      </>
      }

      {congregations.length === 0 &&
      <div style={{fontSize: '150%', maxWidth: '20em', textAlign: 'center'}}>
        {!settings.user &&
        <p><LoginButton/></p>
        }
        <p><RegisterButton/></p>
        <p><JoinButton/></p>
      </div>
      }
    </>
  );
};

export default OverviewPage;
