// Copyright © 2015-2023 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import {Territory, useCongregationById, useSettings} from "../api";
import styles from "./TerritoryListPage.module.css";
import InfoBox from "../maps/InfoBox";
import TerritoryListMap from "../maps/TerritoryListMap";
import {usePageState} from "../util";
import {Link, useNavigate, useParams} from "react-router-dom";
import {Trans, useTranslation} from "react-i18next";
import {auth0Authenticator} from "../authentication.ts";

function LimitedVisibilityHelp() {
  const {t} = useTranslation();
  const settings = useSettings();

  function login() {
    auth0Authenticator(settings).login();
  }

  return (
    <InfoBox title={t('TerritoryListPage.limitedVisibility.title')}>
      <p>{t('TerritoryListPage.limitedVisibility.explanation')}
        {' '}
        {settings.user ?
          <Trans i18nKey="TerritoryListPage.limitedVisibility.needToRequestAccess">
            You will need to <Link to="/join">request access</Link> to see the rest.</Trans> :
          <Trans i18nKey="TerritoryListPage.limitedVisibility.needToLogin">
            You will need to <Link to="#" onClick={login}>login</Link> to see the rest.</Trans>}
      </p>
    </InfoBox>
  );
}

function SearchForm({search, setSearch}) {
  const {t} = useTranslation();
  const id = 'territory-search';
  return (
    <form className={styles.search + " pure-form"}
          onSubmit={event => event.preventDefault()}>
      <label htmlFor={id}>{t('TerritoryListPage.search')}</label>
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
          {t('TerritoryListPage.clear')}
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

const TerritoryListPage = () => {
  const {t, i18n} = useTranslation();
  const {congregationId} = useParams()
  const navigate = useNavigate();
  const congregation = useCongregationById(congregationId);
  const [search, setSearch] = usePageState('search', '');
  const visibleTerritories = congregation.territories.filter(territory => matchesSearch(territory, search));
  return <>
    <h1>{t('Navigation.territories')}</h1>
    {!congregation.permissions.viewCongregation &&
      <LimitedVisibilityHelp/>
    }
    <div className={styles.map}>
      <TerritoryListMap congregation={congregation}
                        territories={visibleTerritories}
                        onClick={territoryId => {
                          navigate(territoryId, {});
                        }}
                        key={i18n.resolvedLanguage}/>
    </div>
    <SearchForm search={search} setSearch={setSearch}/>
    <table className="pure-table pure-table-striped">
      <thead>
      <tr>
        <th>{t('Territory.number')}</th>
        <th>{t('Territory.region')}</th>
        <th>{t('Territory.addresses')}</th>
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
