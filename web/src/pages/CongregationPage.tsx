// Copyright © 2015-2023 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import {useCongregationById} from "../api";
import {Link, useParams} from "react-router-dom";
import {useTranslation} from "react-i18next";
import PageTitle from "../layout/PageTitle.tsx";
import DemoDisclaimer from "./DemoDisclaimer.tsx";

const CongregationPage = () => {
  const {t} = useTranslation();
  const {congregationId} = useParams()
  const congregation = useCongregationById(congregationId);
  return <>
    <DemoDisclaimer/>
    <PageTitle title={congregation.name}/>
    <p><Link to="territories">{t('TerritoryListPage.title')}</Link></p>
    {congregation.permissions.viewCongregation &&
      <p><Link to="printouts">{t('PrintoutPage.title')}</Link></p>
    }
    {(congregation.permissions.configureCongregation || congregation.permissions.gisAccess) &&
      <p><Link to="settings">{t('SettingsPage.title')}</Link></p>
    }
  </>;
};

export default CongregationPage;
