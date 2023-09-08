// Copyright © 2015-2023 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import {useEffect, useState} from "react";
import {openShare} from "../api";
import {useNavigate, useParams} from "react-router-dom";
import PageTitle from "../layout/PageTitle.tsx";
import {useTranslation} from "react-i18next";

const OpenSharePage = () => {
  const {t} = useTranslation();
  const {shareKey} = useParams()
  const navigate = useNavigate();
  const [error, setError] = useState(null);

  useEffect(() => {
    (async () => {
      try {
        const share = await openShare(shareKey);
        navigate(`/congregation/${share.congregation}/territories/${share.territory}`,
          {replace: true});
      } catch (e) {
        setError(e);
      }
    })();
  }, []);

  if (error) {
    return <>
      <PageTitle title={t('Errors.linkNotFound.title')}/>
      <p>{t('Errors.linkNotFound.description')}</p>
    </>;
  } else {
    return <p>{t('Errors.pleaseWait')}</p>;
  }
};

export default OpenSharePage;
