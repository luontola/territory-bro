// Copyright © 2015-2020 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import React from "react";
import ReactDOM from "react-dom";
import {globalHistory, Router} from "@reach/router"
import {IntlProvider} from "react-intl";
import {language, messages} from "./intl";
import ErrorBoundary from "react-error-boundary";
import OverviewPage from "./pages/OverviewPage";
import CongregationPage from "./pages/CongregationPage";
import RegistrationPage from "./pages/RegistrationPage";
import LoginCallbackPage from "./pages/LoginCallbackPage";
import NotFoundPage from "./pages/NotFoundPage";
import Layout from "./layout/Layout";
import PrintoutPage from "./pages/PrintoutPage";
import ErrorPage from "./pages/ErrorPage";
import SupportPage from "./pages/SupportPage";
import {logPageView} from "./analytics";
import SettingsPage from "./pages/SettingsPage";
import UsersPage from "./pages/UsersPage";
import JoinPage from "./pages/JoinPage";

globalHistory.listen(({location, action}) => {
  console.info(`Current URL is now ${location.pathname}${location.search}${location.hash} (${action})`);
  logPageView();
});

document.querySelector('html').setAttribute('lang', language);

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <ErrorBoundary FallbackComponent={ErrorPage}>
      <IntlProvider locale={language} messages={messages}>
        <React.Suspense fallback={<p>Loading....</p>}>
          <Layout>
            <Router>
              <OverviewPage path="/"/>
              <JoinPage path="/join"/>
              <LoginCallbackPage path="/login-callback"/>
              <RegistrationPage path="/register"/>
              <SupportPage path="/support"/>

              <CongregationPage path="/congregation/:congregationId"/>
              <PrintoutPage path="/congregation/:congregationId/printouts"/>
              <SettingsPage path="/congregation/:congregationId/settings"/>
              <UsersPage path="/congregation/:congregationId/users"/>

              <NotFoundPage default/>
            </Router>
          </Layout>
        </React.Suspense>
      </IntlProvider>
    </ErrorBoundary>
  </React.StrictMode>
);
