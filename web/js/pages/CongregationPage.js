// Copyright © 2015-2019 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import React from "react";
import {Link} from "@reach/router";
import {getCongregationById} from "../api";

const CongregationPage = ({congregationId}) => {
  const congregation = getCongregationById(congregationId);
  return (
    <>
      <h1>{congregation.name}</h1>
      <p><Link to="printouts">Printouts</Link></p>
      {/* TODO: show download link only if the user has GIS access */}
      <p><a href={`/api/congregation/${congregationId}/qgis-project`}>Download QGIS project</a></p>
      <p><Link to="settings">Settings</Link></p>
    </>
  );
};

export default CongregationPage;
