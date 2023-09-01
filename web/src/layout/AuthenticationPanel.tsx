// Copyright © 2015-2023 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import LoginButton from "./LoginButton";
import LogoutButton from "./LogoutButton";
import DevLoginButton from "./DevLoginButton";
import {useSettings} from "../api";
import {useTranslation} from "react-i18next";
import {faUserLarge} from "@fortawesome/free-solid-svg-icons";
import {FontAwesomeIcon} from "@fortawesome/react-fontawesome";

let AuthenticationPanel = () => {
  const {t} = useTranslation();
  const settings = useSettings();
  const dev = settings.dev;
  if (settings.user) {
    const fullName = settings.user.name;
    return <>
      <FontAwesomeIcon icon={faUserLarge} style={{fontSize: "1.25em", verticalAlign: "middle"}}/>
      {` ${fullName} `}
      <LogoutButton/>
    </>;
  } else {
    return <>
      <LoginButton/> {dev && <DevLoginButton/>}
    </>;
  }
};

export default AuthenticationPanel;
