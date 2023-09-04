// Copyright © 2015-2023 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import {useTranslation} from "react-i18next";
import {faLanguage} from "@fortawesome/free-solid-svg-icons";
import {FontAwesomeIcon} from "@fortawesome/react-fontawesome";
import {changeLanguage, languages} from "../i18n.ts";
import {Field, Form, Formik} from "formik";
import {useEffect, useState} from "react";
import styles from "./Layout.module.css";

function formatName(englishName: string, nativeName: string) {
  if (englishName === nativeName) {
    return nativeName
  } else {
    return nativeName + ' - ' + englishName
  }
}

let LanguageSelection = () => {
  const {t, i18n} = useTranslation();
  const [focus, setFocus] = useState(false)
  const initialValues = {language: i18n.resolvedLanguage as string}
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
                               title={t('Navigation.changeLanguage')}
                               className={styles.languageSelectionIcon}/>
              {' '}
              <Field name="language"
                     component="select"
                     aria-label={t('Navigation.changeLanguage')}
                     title={t('Navigation.changeLanguage')}
                     className={styles.languageSelection}
                     onFocus={() => setFocus(true)}
                     onBlur={() => setFocus(false)}>
                {languages.map(({code, englishName, nativeName}) =>
                  <option key={code} value={code}>
                    {focus && formatName(englishName, nativeName)}
                    {/* when this field doesn't have focus, hide all options except
                        the selected option, in order to fit the element's width
                        to the selected option */
                      !focus && values.language === code && nativeName}
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
