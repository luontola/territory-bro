// Copyright © 2015-2024 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import {logout} from "../api";
import {useTranslation} from "react-i18next";

const LogoutButton = () => {
  const {t} = useTranslation();
  return <button id="logout-button" type="button" className="pure-button"
                 onClick={logout}>{t('Navigation.logout')}</button>;
};

export default LogoutButton;
