// Copyright © 2015-2019 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

const GA_MEASUREMENT_ID = 'UA-5984051-10';

window.dataLayer = window.dataLayer || [];

function gtag() {
  dataLayer.push(arguments);
}

export function logPageView() {
  const location = document.location;
  gtag('config', GA_MEASUREMENT_ID, {
    'page_path': `${location.pathname}${location.search}`
  });
}

export function logFatalException(description) {
  gtag('event', 'exception', {
    'description': description,
    'fatal': true
  });
}

gtag('js', new Date());
logPageView();
