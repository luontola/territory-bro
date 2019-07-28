// Copyright © 2015-2019 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import React from "react";
import {getSettings} from "../api";
import {buildAuthenticator} from "../authentication";
import {logFatalException} from "../analytics";

function login() {
  const settings = getSettings();
  const {domain, clientId} = settings.auth0;
  const auth = buildAuthenticator(domain, clientId);
  auth.login();
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

const ErrorPage = ({componentStack, error}) => {
  const httpStatus = error.isAxiosError && error.response && error.response.status;
  if (httpStatus === 401) {
    login();
    return <p>Logging in...</p>;
  }
  let title;
  let description = `${formatHttpError(error)}${error.stack}\n\nThe error is located at:${componentStack}`;
  if (httpStatus === 403) {
    title = "Not authorized 🛑";
  } else {
    title = "Sorry, something went wrong 🥺";
    logFatalException(description);
  }
  return (
    <>
      <h1>{title}</h1>
      <p><a href="/">Return to the front page and try again</a></p>
      <pre>{description}</pre>
    </>
  );
};

export default ErrorPage;
