// Copyright © 2015-2018 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import "@babel/polyfill"
import React from "react";
import ReactDOM from "react-dom";
import {Provider} from "react-redux";
import {applyMiddleware, createStore} from "redux";
import {createLogger} from "redux-logger";
import reducers from "./reducers";
import history from "./history";
import router from "./router";
import routes from "./routes";
import {IntlProvider} from "react-intl";
import {language, messages} from "./intl";
import {congregationChanged, mapRastersLoaded} from "./configActions";
import {mapRasters} from "./maps/mapOptions";
import {savedCongregationId} from "./congregation";

const logger = createLogger();
const store = createStore(reducers, applyMiddleware(logger));
const root = document.getElementById('root');
if (root === null) {
  throw new Error('root element not found');
}

function renderComponent(component) {
  ReactDOM.render(
    <IntlProvider locale={language} messages={messages}>
      <Provider store={store}>
        {component}
      </Provider>
    </IntlProvider>, root);
}

function render(location) {
  router.resolve(routes, {...location, store})
    .then(renderComponent)
    .catch(error => {
      console.error("Error in rendering " + location.pathname + "\n", error);
      router.resolve(routes, {...location, store, error})
        .then(renderComponent);
    });
}

const congregationId = savedCongregationId();
if (congregationId) {
  store.dispatch(congregationChanged(congregationId));
}
store.dispatch(mapRastersLoaded(mapRasters));

render(history.location);
history.listen((location, action) => {
  console.log(`Current URL is now ${location.pathname}${location.search}${location.hash} (${action})`);
  render(location);
});
