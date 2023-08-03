// Copyright © 2015-2023 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import {getCongregationById} from "../api";
import {Link, useParams} from "react-router-dom";
import InfoBox from "../maps/InfoBox";

const CongregationPage = () => {
  const {congregationId} = useParams()
  const congregation = getCongregationById(congregationId);
  return <>
    <h1>{congregation.name}</h1>
    {congregationId === "demo" &&
      <InfoBox title="Welcome to the demo">
        <p>This demo is limited to only viewing a congregation. Some features are restricted.</p>
        <p>For example, you won't be able to edit territories, share links to territories, and the printouts won't have
          the QR code that links to the territory.</p>
      </InfoBox>}
    <p><Link to="territories">Territories</Link></p>
    {congregation.permissions.viewCongregation &&
      <p><Link to="printouts">Printouts</Link></p>}
    {congregation.permissions.gisAccess &&
      <p><a href={`/api/congregation/${congregationId}/qgis-project`}>Download QGIS project</a></p>}
    {congregation.permissions.configureCongregation && <>
      <p><Link to="users">Users</Link></p>
      <p><Link to="settings">Settings</Link></p>
    </>}
  </>;
};

export default CongregationPage;
