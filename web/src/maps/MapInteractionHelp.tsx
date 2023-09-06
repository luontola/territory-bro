// Copyright © 2015-2023 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import InfoBox from "./InfoBox";
import {Trans, useTranslation} from "react-i18next";

const MapInteractionHelp = () => {
  const {t} = useTranslation();
  const mac = navigator.platform.startsWith('Mac');
  const ctrl = mac ? 'Cmd' : 'Ctrl';
  const alt = mac ? 'Option' : 'Alt';
  return <InfoBox title={t('MapInteractionHelp.title')}>
    <p><Trans i18nKey="MapInteractionHelp.move"/></p>
    <p><Trans i18nKey="MapInteractionHelp.zoom" values={{ctrl}}/></p>
    <p><Trans i18nKey="MapInteractionHelp.rotate" values={{alt}}/></p>
  </InfoBox>
};

export default MapInteractionHelp;
