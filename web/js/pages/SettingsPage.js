// Copyright © 2015-2019 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import React from "react";
import {getCongregationById} from "../api";
import {Link} from "@reach/router";

const SettingsPage = ({congregationId}) => {
  const congregation = getCongregationById(congregationId);
  return (
    <>
      <h1><Link to="..">{congregation.name}</Link>: Settings</h1>
      <p>{congregation.name}</p>
    </>
  );
};

export default SettingsPage;
