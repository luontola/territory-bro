// Copyright © 2015-2023 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import {useCongregations, useSettings} from "../api";
import LoginButton from "../layout/LoginButton";
import {Link} from "react-router-dom";
import {Trans, useTranslation} from "react-i18next";

// TODO: move QGIS project download to congregation page
function QgisProjectSection({
                              territoryCount,
                              regionCount,
                              congregationIds,
                              supportEmail
                            }) {
  return <>
    <h2>QGIS Project</h2>

    <p>Your congregation's database has currently {territoryCount} territories and {regionCount} regions.</p>

    <p>To add and edit the territories in your database, first download and install <a href="https://qgis.org/">
      QGIS 3.4 (Long Term Release)</a>.</p>

    <p>Then download the QGIS project file for your congregation and open it in QGIS:</p>

    <ul>
      {congregationIds.map(congregationId => <li key={congregationId}>
        <a href={`/api/download-qgis-project/${congregationId}`} rel="nofollow">
          {congregationId}-territories.qgs</a></li>)}
    </ul>

    <p>For more help, read the guides at <a
      href="https://territorybro.com/guide/">https://territorybro.com/guide/</a> or ask <a
      href={`mailto:${supportEmail}`}>{supportEmail}</a>.
    </p>
  </>;
}

const ViewDemoButton = () => {
  const {t} = useTranslation();
  return <Link to="/congregation/demo" className="pure-button">{t('HomePage.viewDemo')}</Link>;
};
const RegisterButton = () => {
  const {t} = useTranslation();
  return <Link to="/register" className="pure-button">{t('RegistrationPage.title')}</Link>;
};
const JoinButton = () => {
  const {t} = useTranslation();
  return <Link to="/join" className="pure-button">{t('JoinPage.title')}</Link>;
};

const HomePage = () => {
  const {t} = useTranslation();
  const settings = useSettings();
  const congregations = useCongregations();
  return <>
    <h1>Territory Bro</h1>

    <p><Trans i18nKey="HomePage.introduction">
      <a href="https://territorybro.com"></a>
    </Trans></p>

    {congregations.length > 0 && <>
      <h2>{t('HomePage.yourCongregations')}</h2>
      <ul>
        {congregations.map(cong => <li key={cong.id} style={{fontSize: '150%'}}><Link
          to={`/congregation/${cong.id}`}>{cong.name}</Link></li>)}
      </ul>
      <p style={{paddingTop: '1.5em'}}>
        {settings.demoAvailable && <ViewDemoButton/>} <RegisterButton/> <JoinButton/>
      </p>
    </>}

    {congregations.length === 0 && <div style={{fontSize: '150%', maxWidth: '20em', textAlign: 'center'}}>
      {!settings.user && <p><LoginButton/></p>}
      {settings.demoAvailable && <p><ViewDemoButton/></p>}
      <p><RegisterButton/></p>
      <p><JoinButton/></p>
    </div>}
  </>;
};

export default HomePage;
