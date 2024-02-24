// Copyright © 2015-2024 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import {appLogout, useSettings} from "../api";
import {useTranslation} from "react-i18next";
import {auth0Authenticator} from "../authentication.ts";

const LogoutButton = () => {
  const {t} = useTranslation();
  const settings = useSettings();
  const onClick = async () => {
    await appLogout()
    auth0Authenticator(settings).logout();
  };
  return <button id="logout-button" type="button" className="pure-button"
                 onClick={onClick}>{t('Navigation.logout')}</button>;
};

export default LogoutButton;
