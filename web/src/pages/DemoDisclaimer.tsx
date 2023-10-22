// Copyright © 2015-2023 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import {useParams} from "react-router-dom";
import InfoBox from "../maps/InfoBox";
import {useTranslation} from "react-i18next";

const DemoDisclaimer = () => {
  const {t} = useTranslation();
  const {congregationId} = useParams()
  return <>
    {congregationId === "demo" &&
      <InfoBox title={t('DemoDisclaimer.welcome')}>
        <p>{t('DemoDisclaimer.introduction')}</p>
      </InfoBox>
    }
  </>;
};

export default DemoDisclaimer;
