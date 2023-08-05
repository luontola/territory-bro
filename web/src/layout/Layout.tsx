// Copyright © 2015-2023 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import "./defaultStyles";
import styles from "./Layout.module.css";
import {ReactNode, useEffect} from "react";
import AuthenticationPanel from "./AuthenticationPanel";
import {useCongregationById} from "../api";
import {NavLink as RouterNavLink, Route, Routes, useParams} from "react-router-dom";

const NavLink = (props) => (
  <RouterNavLink className={({isActive}) => isActive ? styles.active : ""} {...props}/>
);

const HomeNav = ({}) => {
  return (
    <ul className={styles.nav}>
      <li><NavLink to="/">Home</NavLink></li>
      <li><a href="https://territorybro.com/guide/">
        User Guide <i className="fas fa-external-link-alt" title="External link"/></a></li>
      <li><a href="https://groups.google.com/forum/#!forum/territory-bro-announcements">
        News <i className="fas fa-external-link-alt" title="External link"/></a></li>
      <li><NavLink to="/help">Help</NavLink></li>
    </ul>
  );
}

const CongregationNav = () => {
  const {congregationId} = useParams()
  const congregation = useCongregationById(congregationId);
  return (
    <ul className={styles.nav}>
      <li><NavLink to="/">Home</NavLink></li>
      <li><NavLink to=".">{congregation.name}</NavLink></li>
      <li><NavLink to="territories">Territories</NavLink></li>
      {congregation.permissions.viewCongregation &&
        <li><NavLink to="printouts">Printouts</NavLink></li>}
      {congregation.permissions.configureCongregation && <>
        <li><NavLink to="users">Users</NavLink></li>
        <li><NavLink to="settings">Settings</NavLink></li>
      </>}
      <li><NavLink to="/help">Help</NavLink></li>
    </ul>
  );
}

type Props = {
  title?: string;
  children?: ReactNode;
};

const Layout = ({title, children}: Props) => {
  useEffect(() => {
    // TODO: get title from H1
    const site = "Territory Bro";
    document.title = title ? `${title} - ${site}` : site;
  }, []);

  return <>
    <nav className={`${styles.navbar} no-print`}>
      <Routes>
        <Route path="/*" element={<HomeNav/>}/>
        <Route path="/congregation/:congregationId/*" element={<CongregationNav/>}/>
      </Routes>
      <div className={styles.auth}>
        <AuthenticationPanel/>
      </div>
    </nav>

    <main className={styles.content}>
      {children}
    </main>
  </>;
}

export default Layout;
