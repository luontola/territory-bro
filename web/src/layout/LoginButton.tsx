// Copyright © 2015-2024 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import {auth0Authenticator} from "../authentication";
import {useSettings} from "../api";
import {useTranslation} from "react-i18next";

const LoginButton = () => {
  const {t} = useTranslation();
  const settings = useSettings();
  return <button id="login-button" type="button" className="pure-button" onClick={() => {
    auth0Authenticator(settings).login();
  }}>{t('Navigation.login')}</button>;
};

export default LoginButton;
