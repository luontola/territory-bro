// Copyright © 2015-2022 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import React from "react";
import ReactDOM from "react-dom";
import {globalHistory, Router} from "@reach/router";
import {IntlProvider} from "react-intl";
import {language, messages} from "./intl";
import ErrorBoundary from "react-error-boundary";
import HomePage from "./pages/HomePage";
import CongregationPage from "./pages/CongregationPage";
import RegistrationPage from "./pages/RegistrationPage";
import LoginCallbackPage from "./pages/LoginCallbackPage";
import NotFoundPage from "./pages/NotFoundPage";
import Layout from "./layout/Layout";
import PrintoutPage from "./pages/PrintoutPage";
import ErrorPage from "./pages/ErrorPage";
import HelpPage from "./pages/HelpPage";
import {logPageView} from "./analytics";
import SettingsPage from "./pages/SettingsPage";
import UsersPage from "./pages/UsersPage";
import JoinPage from "./pages/JoinPage";
import TerritoryListPage from "./pages/TerritoryListPage";
import TerritoryPage from "./pages/TerritoryPage";
import OpenSharePage from "./pages/OpenSharePage";
import {getPageState, setPageState} from "./util";

let previousLocation = globalHistory.location;
globalHistory.listen(({location, action}) => {
  if (previousLocation.href === location.href) {
    return; // only page state was updated, but the URL remains the same
  }
  previousLocation = location;

  console.info(`Current URL is now ${location.pathname}${location.search}${location.hash} (${action})`);
  logPageView();

  const scrollY = getPageState("scrollY");
  if (typeof scrollY === 'number') {
    // XXX: scrolling immediately does not scroll far enough; maybe we must wait for React to render the whole page
    setTimeout(() => {
      window.scrollTo(0, scrollY);
    }, 100);
  } else {
    document.querySelector('main')?.scrollIntoView({block: "start"});
  }
});

function listenScrollY(onScroll) {
  let lastKnownScrollY = 0;
  let ticking = false;
  window.addEventListener('scroll', _event => {
    lastKnownScrollY = window.scrollY;
    if (!ticking) {
      // avoiding firing too many events, as advised in https://developer.mozilla.org/en-US/docs/Web/API/Element/scroll_event
      setTimeout(() => {
        onScroll(lastKnownScrollY);
        ticking = false;
      }, 200);
      ticking = true;
    }
  });
}

listenScrollY(scrollY => {
  setPageState('scrollY', scrollY);
})

document.querySelector('html')
  .setAttribute('lang', language);

ReactDOM.createRoot(document.getElementById('root'))
  .render(
    <React.StrictMode>
      <ErrorBoundary FallbackComponent={ErrorPage}>
        <IntlProvider locale={language} messages={messages}>
          <React.Suspense fallback={<p>Loading....</p>}>
            <Layout>
              <Router>
                <HomePage path="/"/>
                <JoinPage path="/join"/>
                <LoginCallbackPage path="/login-callback"/>
                <RegistrationPage path="/register"/>
                <HelpPage path="/help"/>
                <OpenSharePage path="/share/:shareKey"/>

                <CongregationPage path="/congregation/:congregationId"/>
                <TerritoryListPage path="/congregation/:congregationId/territories"/>
                <TerritoryPage path="/congregation/:congregationId/territories/:territoryId"/>
                <PrintoutPage path="/congregation/:congregationId/printouts"/>
                <SettingsPage path="/congregation/:congregationId/settings"/>
                <UsersPage path="/congregation/:congregationId/users"/>

                <NotFoundPage default/>
              </Router>
            </Layout>
          </React.Suspense>
        </IntlProvider>
      </ErrorBoundary>
    </React.StrictMode>);
