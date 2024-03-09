// Copyright © 2015-2024 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import {useSettings} from "../api";
import {auth0Authenticator} from "../authentication";
import {Trans, useTranslation} from "react-i18next";
import PageTitle from "../layout/PageTitle.tsx";
import {installCopyToClipboard} from "../clipboard.ts";

installCopyToClipboard('#copy-your-user-id');

const JoinPage = () => {
  const {t} = useTranslation();
  const settings = useSettings();
  // TODO: deduplicate with registration page
  if (!settings.user) {
    auth0Authenticator(settings).login();
    return <p>Please wait, you will be redirected...</p>;
  }
  const userId = settings.user.id;

  return <>
    <PageTitle title={t('JoinPage.title')}/>

    <p><Trans i18nKey="JoinPage.introduction"/></p>

    <p>{t('JoinPage.yourUserId')}</p>

    <p id="your-user-id" style={{fontSize: '150%', margin: '15px'}}>{userId}</p>

    <p>
      <button id="copy-your-user-id" type="button" className="pure-button" data-clipboard-target="#your-user-id">
        {t('JoinPage.copy')}
      </button>
    </p>
  </>;
};

export default JoinPage;
