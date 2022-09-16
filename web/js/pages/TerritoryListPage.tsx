// Copyright © 2015-2022 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import React from "react";
import {Link} from "@reach/router";
import {getCongregationById, getSettings, Territory} from "../api";
import styles from "./TerritoryListPage.css";
import InfoBox from "../maps/InfoBox";
import TerritoryListMap from "../maps/TerritoryListMap";
import {usePageState} from "../util";

function LimitedVisibilityHelp() {
  const settings = getSettings();
  return (
    <InfoBox title={"Why so few territories?"}>
      <p>Only those territories which have been shared with you are currently shown.
        {settings.user ?
          <> You will need to <Link to="/join">request access</Link> to see the rest.</> :
          <> You will need to login to see the rest.</>}
      </p>
    </InfoBox>
  );
}

function SearchForm({search, setSearch}) {
  const id = 'territory-search';
  return (
    <form className={styles.search + " pure-form"}
          onSubmit={event => event.preventDefault()}>
      <label htmlFor={id}>Search</label>
      <input id={id}
             type="text"
             className="pure-input-rounded"
             autoComplete="off"
             value={search}
             onChange={event => {
               setSearch(event.target.value);
             }}/>
      {search !== '' &&
        <button type="button"
                className="pure-button"
                onClick={() => {
                  setSearch('');
                  document.getElementById(id).focus();
                }}>
          Clear
        </button>
      }
    </form>
  );
}

function matchesSearch(territory: Territory, search: string): boolean {
  if (search === '') {
    return true;
  }
  const {number, region, addresses} = territory;
  search = search.toLowerCase();
  return number.toLowerCase().includes(search)
    || region.toLowerCase().includes(search)
    || addresses.toLowerCase().includes(search);
}

const TerritoryListPage = ({congregationId, navigate}) => {
  const congregation = getCongregationById(congregationId);
  const [search, setSearch] = usePageState('search', '');
  const visibleTerritories = congregation.territories.filter(territory => matchesSearch(territory, search));
  return <>
    <h1>Territories</h1>
    {!congregation.permissions.viewCongregation &&
      <LimitedVisibilityHelp/>
    }
    <div className={styles.map}>
      <TerritoryListMap congregation={congregation}
                        territories={visibleTerritories}
                        onClick={territoryId => {
                          navigate(territoryId, {});
                        }}/>
    </div>
    <SearchForm search={search} setSearch={setSearch}/>
    <table className="pure-table pure-table-striped">
      <thead>
      <tr>
        <th>Number</th>
        <th>Region</th>
        <th>Addresses</th>
      </tr>
      </thead>
      <tbody>
      {visibleTerritories.map(territory =>
        <tr key={territory.id}>
          <td className={styles.number}>
            <Link to={territory.id}>{territory.number === '' ? '-' : territory.number}</Link>
          </td>
          <td>{territory.region}</td>
          <td>{territory.addresses}</td>
        </tr>
      )}
      </tbody>
    </table>
  </>;
};

export default TerritoryListPage;
