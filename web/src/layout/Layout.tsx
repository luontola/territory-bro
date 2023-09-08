// Copyright © 2015-2023 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import "./defaultStyles";
import styles from "./Layout.module.css";
import {ReactNode} from "react";
import AuthenticationPanel from "./AuthenticationPanel";
import {useCongregationById} from "../api";
import {NavLink as RouterNavLink, Route, Routes, useParams} from "react-router-dom";
import {useTranslation} from "react-i18next";
import {faExternalLinkAlt} from "@fortawesome/free-solid-svg-icons";
import {FontAwesomeIcon} from "@fortawesome/react-fontawesome";
import LanguageSelection from "./LanguageSelection.tsx";

const NavLink = (props) => (
  <RouterNavLink className={({isActive}) => isActive ? styles.active : ""} {...props}/>
);

const HomeNav = ({}) => {
  const {t} = useTranslation();
  return (
    <ul className={styles.nav}>
      <li><NavLink to="/">{t('HomePage.title')}</NavLink></li>
      <li><a href="https://territorybro.com/guide/" target="_blank">
        {t('Navigation.userGuide')} <FontAwesomeIcon icon={faExternalLinkAlt} title={t('Navigation.opensInNewWindow')}/>
      </a></li>
      <li><a href="https://groups.google.com/g/territory-bro-announcements" target="_blank">
        {t('Navigation.news')} <FontAwesomeIcon icon={faExternalLinkAlt} title={t('Navigation.opensInNewWindow')}/>
      </a></li>
      <li><NavLink to="/support">{t('SupportPage.title')}</NavLink></li>
    </ul>
  );
}

const CongregationNav = () => {
  const {congregationId} = useParams()
  const congregation = useCongregationById(congregationId);
  const {t} = useTranslation();
  return (
    <ul className={styles.nav}>
      <li><NavLink to="/">{t('HomePage.title')}</NavLink></li>
      <li><NavLink to=".">{congregation.name}</NavLink></li>
      <li><NavLink to="territories">{t('TerritoryListPage.title')}</NavLink></li>
      {congregation.permissions.viewCongregation &&
        <li><NavLink to="printouts">{t('PrintoutPage.title')}</NavLink></li>
      }
      {(congregation.permissions.configureCongregation || congregation.permissions.gisAccess) &&
        <li><NavLink to="settings">{t('SettingsPage.title')}</NavLink></li>
      }
      <li><NavLink to="support">{t('SupportPage.title')}</NavLink></li>
    </ul>
  );
}

type Props = {
  title?: string;
  children?: ReactNode;
};

export const FailSafeLayout = ({children}: Props) =>
  <>
    <nav className={`${styles.navbar} no-print`}>
      <HomeNav/>
    </nav>

    <main className={styles.content}>
      {children}
    </main>
  </>

const Layout = ({children}: Props) =>
  <>
    <nav className={`${styles.navbar} no-print`}>
      <Routes>
        <Route path="/*" element={<HomeNav/>}/>
        <Route path="/congregation/:congregationId/*" element={<CongregationNav/>}/>
      </Routes>
      <div className={styles.lang}>
        <LanguageSelection/>
      </div>
      <div className={styles.auth}>
        <AuthenticationPanel/>
      </div>
    </nav>

    <main className={styles.content}>
      {children}
    </main>
  </>

export default Layout;
