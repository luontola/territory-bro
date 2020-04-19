// Copyright © 2015-2020 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import React from "react";
import PrintOptionsForm from "../prints/PrintOptionsForm";
import {getCongregationById} from "../api";
import {Link} from "@reach/router";
import MapInteractionHelp from "../maps/MapInteractionHelp";

const PrintoutPage = ({congregationId}) => {
  const congregation = getCongregationById(congregationId);
  return <>
    <div className="no-print">
      <h1><Link to="..">{congregation.name}</Link>: Printouts</h1>
    </div>
    <PrintOptionsForm congregationId={congregationId}/>
    <div className="no-print">
      <MapInteractionHelp/>
    </div>
  </>;
};

export default PrintoutPage;
