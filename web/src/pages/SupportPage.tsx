// Copyright © 2015-2023 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import {useSettings} from "../api";
import {Trans, useTranslation} from "react-i18next";
import PageTitle from "../layout/PageTitle.tsx";

const SupportPage = () => {
  const {t} = useTranslation();
  const {supportEmail} = useSettings();
  return <>
    <PageTitle title={t('SupportPage.title')}/>
    <p><Trans i18nKey="SupportPage.introduction">
      <a href="https://www.luontola.fi/about" target="_blank"></a>
    </Trans></p>
    <p><Trans i18nKey="SupportPage.mailingListAd">
      <a href="https://groups.google.com/g/territory-bro-announcements" target="_blank"></a>
    </Trans></p>
    <p><Trans i18nKey="SupportPage.userGuideAd">
      <a href="https://territorybro.com/guide/" target="_blank"></a>
    </Trans></p>
    {supportEmail &&
      <p><Trans i18nKey="SupportPage.emailAd" values={{email: supportEmail}}>
        <a href={`mailto:${supportEmail}`}></a>
      </Trans></p>}
    <p><Trans i18nKey="SupportPage.translationAd">
      <a href="https://github.com/luontola/territory-bro/tree/master/web/src/locales#readme" target="_blank"></a>
    </Trans></p>
    <p><Trans i18nKey="SupportPage.issueTrackerAd">
      <a href="https://github.com/luontola/territory-bro/issues" target="_blank"></a>
    </Trans></p>
  </>;
};

export default SupportPage;
