// Copyright © 2015-2020 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import React from "react";
import {Link} from "@reach/router";
import {getCongregationById} from "../api";
import styles from "./TerritoryPage.css"
import TerritoryMap from "../maps/TerritoryMap";
import OSM from "ol/source/OSM";

const mapRaster = {
  id: '',
  name: '',
  source: new OSM()
};

const TerritoryPage = ({congregationId, territoryId}) => {
  const congregation = getCongregationById(congregationId);
  const territory = congregation.getTerritoryById(territoryId);
  // TODO: consider using a grid layout for responsiveness so that the details area has fixed width
  return <>
    <h1><Link to="../..">{congregation.name}</Link>: <Link to="..">Territories</Link>: Territory {territory.number}</h1>

    <div className="pure-g">
      <div className="pure-u-1 pure-u-sm-2-3 pure-u-md-1-2 pure-u-lg-1-3 pure-u-xl-1-4">
        <div className={styles.details}>
          <table className="pure-table pure-table-horizontal">
            <tbody>
            <tr>
              <th>Number</th>
              <td>{territory.number}</td>
            </tr>
            <tr>
              <th>Region</th>
              <td>{territory.subregion}</td>
            </tr>
            <tr>
              <th>Addresses</th>
              <td>{territory.addresses}</td>
            </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div className="pure-u-1 pure-u-lg-2-3 pure-u-xl-3-4">
        <div className={styles.map}>
          <TerritoryMap territory={territory} mapRaster={mapRaster}/>
        </div>
      </div>
    </div>
  </>;
};

export default TerritoryPage;
