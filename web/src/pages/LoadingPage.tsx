// Copyright © 2015-2023 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import {FailSafeLayout} from "../layout/Layout.tsx";
import {useTranslation} from "react-i18next";

const LoadingPage = () => {
  const {t} = useTranslation();
  return (
    <FailSafeLayout>
      <p>{t('Errors.pleaseWait')}</p>
    </FailSafeLayout>
  );
};

export default LoadingPage;
