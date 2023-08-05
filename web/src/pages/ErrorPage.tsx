// Copyright © 2015-2023 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import {useSettings} from "../api";
import {buildAuthenticator} from "../authentication";
import {formatError, logFatalException} from "../analytics";

export function axiosHttpStatus(error) {
  if (error.isAxiosError) {
    return error.response?.status
  }
}

const ErrorPage = ({componentStack, error}) => {
  const settings = useSettings();
  const httpStatus = axiosHttpStatus(error);
  if (httpStatus === 401) {
    console.log("Logging in the user in response to HTTP 401 Unauthorized")
    const {
      domain,
      clientId
    } = settings.auth0;
    const auth = buildAuthenticator(domain, clientId);
    auth.login();
    return <p>Logging in...</p>;
  }
  const description = `${formatError(error)}\n\nThe error is located at:${componentStack}`;
  let title;
  if (httpStatus === 403) {
    title = "Not authorized 🛑";
  } else {
    title = "Sorry, something went wrong 🥺";
    logFatalException(description);
  }
  return <>
    <h1>{title}</h1>
    <p><a href="/">Return to the front page and try again</a></p>
    <pre>{description}</pre>
  </>;
};

export default ErrorPage;
