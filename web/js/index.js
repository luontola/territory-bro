// Copyright © 2015-2017 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import "babel-polyfill";
import React from "react";
import ReactDOM from "react-dom";
import Provider from "react-redux/es/components/Provider";
import applyMiddleware from "redux/es/applyMiddleware";
import createStore from "redux/es/createStore";
import {createLogger} from "redux-logger";
import reducers from "./reducers";
import history from "./history";
import router from "./router";
import routes from "./routes";
import {addLocaleData, IntlProvider} from "react-intl";
import en from "react-intl/locale-data/en";
import fi from "react-intl/locale-data/fi";
import pt from "react-intl/locale-data/pt";

addLocaleData([...en, ...fi, ...pt]);
const usersLocale = navigator.language;
const translationsForUsersLocale = {};

const logger = createLogger();
const store = createStore(reducers, applyMiddleware(logger));
const root = document.getElementById('root');

function renderComponent(component) {
  ReactDOM.render(
    <IntlProvider locale={usersLocale} messages={translationsForUsersLocale}>
      <Provider store={store}>
        {component}
      </Provider>
    </IntlProvider>, root);
}

function render(location) {
  router.resolve(routes, location)
    .then(renderComponent)
    .catch(error => {
      console.error("Error in rendering " + location.pathname + "\n", error);
      router.resolve(routes, {...location, error})
        .then(renderComponent);
    });
}

render(history.location);
history.listen((location, action) => {
  console.log(`Current URL is now ${location.pathname}${location.search}${location.hash} (${action})`);
  render(location);
});
