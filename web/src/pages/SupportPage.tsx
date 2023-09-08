// Copyright © 2015-2023 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import {useSettings} from "../api";
import {Trans, useTranslation} from "react-i18next";

const SupportPage = () => {
  const {t} = useTranslation();
  const {supportEmail} = useSettings();
  return <>
    <h1>{t('SupportPage.title')}</h1>
    <p><Trans i18nKey="SupportPage.introduction">
      <a href="https://www.luontola.fi/about"></a>
    </Trans></p>
    <p><Trans i18nKey="SupportPage.userGuideAd">
      <a href="https://territorybro.com/guide/"></a>
    </Trans></p>
    {supportEmail &&
      <p><Trans i18nKey="SupportPage.emailAd" values={{email: supportEmail}}>
        <a href={`mailto:${supportEmail}`}></a>
      </Trans></p>}
    <p><Trans i18nKey="SupportPage.issueTrackerAd">
      <a href="https://github.com/luontola/territory-bro/issues"></a>
    </Trans></p>
  </>;
};

export default SupportPage;
