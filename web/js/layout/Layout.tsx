// Copyright © 2015-2020 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import "./defaultStyles";
import styles from "./Layout.css";
import * as React from "react";
import {useEffect} from "react";
import AuthenticationPanel from "./AuthenticationPanel";
import {Link, Router} from "@reach/router";
import {getCongregationById} from "../api";

type Props = {
  title?: string;
  children?: React.ReactNode;
};

const HomeNav = ({}) => {
  return (
    <ul>
      <li><Link to="/">Home</Link></li>
      <li><a href="https://territorybro.com/guide/">User Guide</a></li>
      <li><a href="https://groups.google.com/forum/#!forum/territory-bro-announcements">News</a></li>
      <li><Link to="/help">Help</Link></li>
    </ul>
  );
}

const CongregationNav = ({congregationId}) => {
  const congregation = getCongregationById(congregationId);
  return (
    <ul>
      <li><Link to="/">Home</Link></li>
      <li>{congregation.name}</li>
      <ul>
        <li><Link to="territories">Territories</Link></li>
        <li><Link to="printouts">Printouts</Link></li>
        {congregation.permissions.configureCongregation && <>
          <li><Link to="users">Users</Link></li>
          <li><Link to="settings">Settings</Link></li>
        </>}
      </ul>
      <li><Link to="/help">Help</Link></li>
    </ul>
  );
}

const Layout = ({title, children}: Props) => {
  useEffect(() => {
    // TODO: get title from H1
    const site = "Territory Bro";
    document.title = title ? `${title} - ${site}` : site;
  }, []);

  return <>
    <nav className={`${styles.navigation} no-print`}>
      <AuthenticationPanel/>
      <Router>
        <HomeNav path="/*"/>
        <CongregationNav path="/congregation/:congregationId/*"/>
      </Router>
    </nav>

    <main className={styles.content}>
      {children}
    </main>
  </>;
}

export default Layout;
