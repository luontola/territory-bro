// Copyright © 2015-2023 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import PageTitle from "../layout/PageTitle.tsx";
import {useTranslation} from "react-i18next";

const NotFoundPage = () => {
  const {t} = useTranslation();
  return <>
    <PageTitle title={t('Errors.pageNotFound')}/>
    <p><a href="/">{t('Errors.returnToFrontPage')}</a></p>
  </>;
};

export default NotFoundPage;
