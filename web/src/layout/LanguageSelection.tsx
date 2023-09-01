// Copyright © 2015-2023 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import {useTranslation} from "react-i18next";
import {faLanguage} from "@fortawesome/free-solid-svg-icons";
import {FontAwesomeIcon} from "@fortawesome/react-fontawesome";
import {changeLanguage, languages} from "../i18n.ts";
import {Field, Form, Formik} from "formik";
import {useEffect} from "react";

let LanguageSelection = () => {
  const {t, i18n} = useTranslation();
  const initialValues = {language: i18n.language}
  return (
    <Formik
      initialValues={initialValues}
      onSubmit={() => {
      }}>
      {({values}) => {
        useEffect(() => {
          changeLanguage(values.language);
        }, [values]);
        return <div>
          <Form className="pure-form">
            <label>
              <FontAwesomeIcon icon={faLanguage}
                               style={{fontSize: "2em", verticalAlign: "middle"}}
                               title={t('Navigation.languageSelection')}/>
              {' '}
              <Field name="language" component="select" aria-label={t('Navigation.languageSelection')}>
                {languages.map(({code, englishName, nativeName}) =>
                  <option key={code} value={code}>
                    {nativeName}
                    {englishName === nativeName ? '' : ` - ${englishName}`}
                  </option>)}
              </Field>
            </label>
          </Form>
        </div>
      }}
    </Formik>
  );
};

export default LanguageSelection;
