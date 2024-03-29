// Copyright © 2015-2023 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import {useEffect} from "react";
import {useSettings} from "../api";
import {auth0Authenticator} from "../authentication";
import {sanitizeReturnPath} from "../authenticationUtil";
import {useNavigate} from "react-router-dom";
import {useMutation} from "@tanstack/react-query";
import {useTranslation} from "react-i18next";

const LoginCallbackPage = () => {
  const {t} = useTranslation();
  const navigate = useNavigate();
  const settings = useSettings();

  const auth = useMutation({
    mutationFn: async () => {
      await auth0Authenticator(settings).handleAuthentication();
      const params = new URLSearchParams(document.location.search.substring(1));
      return sanitizeReturnPath(params.get('return') || '/');
    },
    onSuccess: returnPath => {
      navigate(returnPath);
    }
  })

  useEffect(auth.mutate, []);

  if (auth.error) {
    const {error, errorDescription} = auth.error;
    if (error && errorDescription) {
      return <p>Login failed: {error}, {errorDescription}</p>;
    } else {
      return <p>Login failed: {JSON.stringify(auth.error)}</p>;
    }
  }
  return <p>{t('Errors.pleaseWait')} {auth.isLoading ? "⏳" : "⌛️"}</p>;
};

export default LoginCallbackPage;
