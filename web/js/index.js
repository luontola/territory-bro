// Copyright © 2015-2019 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import React from "react";
import ReactDOM from "react-dom";
import {Router} from "@reach/router"
import {Provider} from "react-redux";
import {applyMiddleware, createStore} from "redux";
import {createLogger} from "redux-logger";
import reducers from "./reducers";
import {IntlProvider} from "react-intl";
import {language, messages} from "./intl";
import OverviewPage from "./pages/OverviewPage";
import CongregationPage from "./pages/CongregationPage";
import RegistrationPage from "./pages/RegistrationPage";
import LoginCallbackPage from "./pages/LoginCallbackPage";
import NotFoundPage from "./pages/NotFoundPage";
import Layout from "./layout/Layout";

const logger = createLogger();
const store = createStore(reducers, applyMiddleware(logger));
const root = ReactDOM.unstable_createRoot(document.getElementById('root'));

root.render(
  <React.StrictMode>
    <IntlProvider locale={language} messages={messages}>
      <Provider store={store}>
        <React.Suspense fallback={<p>Loading....</p>}>
          <Layout>
            <Router>
              <OverviewPage path="/"/>
              <CongregationPage path="/congregation/:congregationId"/>
              <RegistrationPage path="/register"/>
              <LoginCallbackPage path="/login-callback"/>
              <NotFoundPage default/>
            </Router>
          </Layout>
        </React.Suspense>
      </Provider>
    </IntlProvider>
  </React.StrictMode>
);
