// Copyright © 2015-2020 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import React, {useState} from "react";
import {Link} from "@reach/router";
import {getCongregationById, Territory} from "../api";
import styles from "./TerritoryListPage.css";

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

const TerritoryListPage = ({congregationId}) => {
  const congregation = getCongregationById(congregationId);
  const [search, setSearch] = useState('')
  return <>
    <h1><Link to="..">{congregation.name}</Link>: Territories</h1>
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
      {congregation.territories
        .filter(territory => matchesSearch(territory, search))
        .map(territory =>
          <tr key={territory.id}>
            <td className={styles.number}>
              <Link to={territory.id}>{territory.number}</Link>
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
