// Copyright © 2015-2020 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import React from "react";
import {Link} from "@reach/router";
import {getCongregationById} from "../api";

const TerritoriesPage = ({congregationId}) => {
  const congregation = getCongregationById(congregationId);
  // TODO: filter search
  return <>
    <h1><Link to="..">{congregation.name}</Link>: Territories</h1>
    <table className="pure-table pure-table-striped">
      <thead>
      <tr>
        <th>Number</th>
        <th>Region</th>
        <th>Addresses</th>
      </tr>
      </thead>
      <tbody>
      {congregation.territories.map(territory =>
        <tr key={territory.id}>
          <td>{territory.number}</td>
          <td>{territory.subregion}</td>
          <td>{territory.addresses}</td>
        </tr>
      )}
      </tbody>
    </table>
  </>;
};

export default TerritoriesPage;
