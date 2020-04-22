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
    <ul className={styles.nav}>
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
    <ul className={styles.nav}>
      <li><Link to="/">Home</Link></li>
      <li>{congregation.name}</li>
      <li><Link to="territories">Territories</Link></li>
      <li><Link to="printouts">Printouts</Link></li>
      {congregation.permissions.configureCongregation && <>
        <li><Link to="users">Users</Link></li>
        <li><Link to="settings">Settings</Link></li>
      </>}
      <li><Link to="/help">Help</Link></li>
    </ul>
  );
}

function RouterComponent({children}) {
  // Workaround for Reach Router to not render in a <div> which messes up flexbox.
  // See https://github.com/reach/router/issues/63#issuecomment-524297867
  return <>{children}</>;
}

const Layout = ({title, children}: Props) => {
  useEffect(() => {
    // TODO: get title from H1
    const site = "Territory Bro";
    document.title = title ? `${title} - ${site}` : site;
  }, []);

  return <>
    <nav className={`${styles.navbar} no-print`}>
      <Router primary={false} component={RouterComponent}>
        <HomeNav path="/*"/>
        <CongregationNav path="/congregation/:congregationId/*"/>
      </Router>
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
