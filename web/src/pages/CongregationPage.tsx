// Copyright © 2015-2023 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import {useCongregationById} from "../api";
import {Link, useParams} from "react-router-dom";
import InfoBox from "../maps/InfoBox";
import {useTranslation} from "react-i18next";

const CongregationPage = () => {
  const {t} = useTranslation();
  const {congregationId} = useParams()
  const congregation = useCongregationById(congregationId);
  return <>
    <h1>{congregation.name}</h1>
    {congregationId === "demo" &&
      <InfoBox title={t('CongregationPage.demo.welcome')}>
        <p>{t('CongregationPage.demo.introduction1')}</p>
        <p>{t('CongregationPage.demo.introduction2')}</p>
      </InfoBox>}
    <p><Link to="territories">{t('Navigation.territories')}</Link></p>
    {congregation.permissions.viewCongregation &&
      <p><Link to="printouts">{t('Navigation.printouts')}</Link></p>}
    {congregation.permissions.gisAccess &&
      <p><a href={`/api/congregation/${congregationId}/qgis-project`}>
        {t('CongregationPage.downloadQgisProject')}</a></p>}
    {congregation.permissions.configureCongregation && <>
      <p><Link to="users">{t('Navigation.users')}</Link></p>
      <p><Link to="settings">{t('Navigation.settings')}</Link></p>
    </>}
  </>;
};

export default CongregationPage;
