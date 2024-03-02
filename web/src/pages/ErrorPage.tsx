// Copyright © 2015-2024 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import {useSettingsSafe} from "../api";
import {auth0Authenticator} from "../authentication";
import {formatError, logFatalException} from "../analytics";
import {FailSafeLayout} from "../layout/Layout.tsx";
import PageTitle from "../layout/PageTitle.tsx";
import {useTranslation} from "react-i18next";
import LoadingPage from "./LoadingPage.tsx";

export function axiosHttpStatus(error) {
  if (error.isAxiosError) {
    return error.response?.status
  }
}

const ErrorPage = ({error}) => {
  const {t} = useTranslation();
  const settings = useSettingsSafe();
  const httpStatus = axiosHttpStatus(error);
  if (httpStatus === 401 && !settings.error) {
    if (settings.data) {
      console.log("Logging in the user in response to HTTP 401 Unauthorized")
      auth0Authenticator(settings.data).login();
    }
    return (
      <LoadingPage/>
    );
  }
  const description = formatError(error);
  let title;
  if (httpStatus === 403) {
    title = t('Errors.accessDenied');
  } else {
    title = t('Errors.unknownError');
    logFatalException(description);
  }
  return (
    <FailSafeLayout>
      <PageTitle title={title}/>
      <p><a href="/">{t('Errors.returnToFrontPage')}</a></p>
      <pre lang="en">{description}</pre>
    </FailSafeLayout>
  );
};

export default ErrorPage;
