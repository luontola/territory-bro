// Copyright © 2015-2019 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import React from "react";
import {getCongregations} from "../api";
import {Link, redirectTo} from "@reach/router";

const CongregationPage = ({congregationId}) => {
  const congregation = getCongregations().find(cong => cong.id === congregationId);
  if (!congregation) {
    redirectTo('/');
  }
  return (
    <>
      <h1>{congregation.name}</h1>
      <ul>
        <li><Link to="territory-cards">Territory Cards</Link></li>
        <li><Link to="neighborhood-maps">Neighborhood Maps</Link></li>
        <li><Link to="rural-territory-cards">Rural Territory Cards</Link></li>
        <li><Link to="region-maps">Region Maps</Link></li>
      </ul>
    </>
  );
};

export default CongregationPage;
