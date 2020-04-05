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

function formatHttpError(error) {
  if (!error.isAxiosError) {
    return '';
  }
  let s = "Request failed:\n";
  s += `    ${error.config.method.toUpperCase()} ${error.config.url}\n`;
  if (error.request.status > 0) {
    s += `    ${error.request.status} ${error.request.statusText}\n`;
  }
  if (error.request.responseText) {
    s += `    ${error.request.responseText}\n`;
  }
  s += "\n";
  return s;
}

export function formatError(error) {
  return formatHttpError(error) + (error.stack || error);
}

gtag('js', new Date());
logPageView();

window.addEventListener('error', event => {
  logFatalException(`Unhandled exception\n${event.message}\n${formatError(event.error)}`);
});

window.addEventListener('unhandledrejection', event => {
  logFatalException(`Unhandled exception (in promise)\n${formatError(event.reason)}`);
});
