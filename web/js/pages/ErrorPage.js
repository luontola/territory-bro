// Copyright © 2015-2019 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import React from "react";
import {getSettings} from "../api";
import {buildAuthenticator} from "../authentication";

function login() {
  const settings = getSettings();
  const {domain, clientId} = settings.auth0;
  const auth = buildAuthenticator(domain, clientId);
  auth.login();
}

function formatHttpError(error) {
  if (!error.isAxiosError) {
    return null;
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
  let message = "Sorry, something went wrong 🥺";
  if (httpStatus === 403) {
    message = "Not authorized 🛑";
  }
  return (
    <>
      <h1>{message}</h1>
      <p><a href="/">Return to the front page and try again</a></p>
      <pre>
        {formatHttpError(error)}
        {`${error.stack}\n\nThe error is located at:${componentStack}`}
      </pre>
    </>
  );
};

export default ErrorPage;
