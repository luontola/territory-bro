// Copyright © 2015-2023 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import {useTranslation} from "react-i18next";
import CongregationSettings from "./CongregationSettings.tsx";
import UserManagement from "./UserManagement.tsx";
import EditingMaps from "./EditingMaps.tsx";
import {useParams} from "react-router-dom";
import {useCongregationById} from "../api.ts";
import styles from "./SettingsPage.module.css";

const SettingsPage = ({}) => {
  const {t} = useTranslation();
  const {congregationId} = useParams()
  const congregation = useCongregationById(congregationId);
  return <>
    <h1>{t('SettingsPage.title')}</h1>
    <div className={styles.sections}>
      {congregation.permissions.configureCongregation && <CongregationSettings/>}
      {congregation.permissions.gisAccess && <EditingMaps/>}
      {congregation.permissions.configureCongregation && <UserManagement/>}
    </div>
  </>;
}

export default SettingsPage;
