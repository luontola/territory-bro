// Copyright © 2015-2023 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import {useTranslation} from "react-i18next";
import CongregationSettings from "./CongregationSettings.tsx";
import UserManagement from "./UserManagement.tsx";

const SettingsPage = ({}) => {
  const {t} = useTranslation();
  return <>
    <h1>{t('SettingsPage.title')}</h1>
    <CongregationSettings/>
    <UserManagement/>
  </>;
}

export default SettingsPage;
