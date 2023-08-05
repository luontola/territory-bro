// Copyright © 2015-2023 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import {useEffect} from "react";
import {useSettings} from "../api";
import {auth0Authenticator} from "../authentication";
import {sanitizeReturnPath} from "../authenticationUtil";
import {useNavigate} from "react-router-dom";

const LoginCallbackPage = () => {
  const navigate = useNavigate();
  const settings = useSettings();

  useEffect(() => {
    (async () => {
      await auth0Authenticator(settings).handleAuthentication();
      const params = new URLSearchParams(document.location.search.substring(1));
      const returnPath = sanitizeReturnPath(params.get('return') || '/');
      navigate(returnPath);
    })();
  });

  return <p>Logging you in...</p>;
};

export default LoginCallbackPage;
