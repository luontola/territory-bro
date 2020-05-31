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

const NavLink = (props) => (
  <Link {...props}
        getProps={({href, isCurrent, isPartiallyCurrent}) => {
          // the object returned here is passed to the anchor element's props
          const active = (href === '/') ? isCurrent : isPartiallyCurrent;
          return {
            className: active ? styles.active : undefined
          };
        }}/>
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

const CongregationNav = ({congregationId}) => {
  const congregation = getCongregationById(congregationId);
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

function RouterComponent({children}) {
  // Workaround for Reach Router to not render in a <div> which messes up flexbox.
  // See https://github.com/reach/router/issues/63#issuecomment-524297867
  return <>{children}</>;
}

type Props = {
  title?: string;
  children?: React.ReactNode;
};

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
