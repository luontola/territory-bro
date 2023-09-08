// Copyright © 2015-2023 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import {useParams} from "react-router-dom";
import {Trans, useTranslation} from "react-i18next";

const EditingMaps = ({}) => {
  const {t} = useTranslation();
  const {congregationId} = useParams()
  return <section>
    <h2>{t('EditingMaps.title')}</h2>
    <p><Trans i18nKey="EditingMaps.introduction">
      <a href="https://territorybro.com/guide/"></a>
      <a href="https://www.qgis.org/"></a>
    </Trans></p>
    <p><a href={`/api/congregation/${congregationId}/qgis-project`} className="pure-button">
      {t('EditingMaps.downloadQgisProject')}
    </a></p>
  </section>;
};

export default EditingMaps;
