// Copyright © 2015-2023 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import {useSettingsSafe} from "../api";
import {auth0Authenticator} from "../authentication";
import {formatError, logFatalException} from "../analytics";
import {FailSafeLayout} from "../layout/Layout.tsx";
import PageTitle from "../layout/PageTitle.tsx";

export function axiosHttpStatus(error) {
  if (error.isAxiosError) {
    return error.response?.status
  }
}

const ErrorPage = ({error}) => {
  const settings = useSettingsSafe();
  const httpStatus = axiosHttpStatus(error);
  if (httpStatus === 401 && !settings.error) {
    if (settings.data) {
      console.log("Logging in the user in response to HTTP 401 Unauthorized")
      auth0Authenticator(settings.data).login();
    }
    return (
      <FailSafeLayout>
        <p>Logging in...</p>
      </FailSafeLayout>
    );
  }
  const description = formatError(error);
  let title;
  if (httpStatus === 403) {
    title = "Not authorized 🛑";
  } else {
    title = "Sorry, something went wrong 🥺";
    logFatalException(description);
  }
  return (
    <FailSafeLayout>
      <PageTitle title={title}/>
      <p><a href="/">Return to the front page and try again</a></p>
      <pre>{description}</pre>
    </FailSafeLayout>
  );
};

export default ErrorPage;
