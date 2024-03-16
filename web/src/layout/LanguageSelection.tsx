// Copyright © 2015-2024 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import {useTranslation} from "react-i18next";
import {faLanguage} from "@fortawesome/free-solid-svg-icons";
import {FontAwesomeIcon} from "@fortawesome/react-fontawesome";
import {changeLanguage, languages} from "../i18n.ts";
import {Field, Form, Formik} from "formik";
import {useEffect, useRef, useState} from "react";
import styles from "./Layout.module.css";

function formatName(englishName: string, nativeName: string) {
  if (englishName === nativeName) {
    return nativeName
  } else {
    return nativeName + ' - ' + englishName
  }
}

const canvas = document.createElement("canvas");
const context = canvas.getContext("2d");

function getTextWidth(text, font) {
  if (!context) {
    return 100;
  }
  context.font = font;
  const metrics = context.measureText(text);
  return metrics.width;
}

function getCssProperty(element, prop) {
  return window.getComputedStyle(element, null).getPropertyValue(prop);
}

function getElementFont(element) {
  const fontWeight = getCssProperty(element, 'font-weight') || 'normal';
  const fontSize = getCssProperty(element, 'font-size') || '16px';
  const fontFamily = getCssProperty(element, 'font-family') || 'Times New Roman';
  return `${fontWeight} ${fontSize} ${fontFamily}`;
}

function adjustDropdownWidthToContent(element?: HTMLSelectElement) {
  if (!element) {
    return;
  }
  const text = element.selectedOptions[0]?.innerText;
  if (!text) {
    return;
  }
  const font = getElementFont(element);
  const hasFocus = element === document.activeElement;
  const dropdownAppearanceAndPaddingWidth = 42; // how much wider the element is on Chrome when it has focus
  const width = Math.ceil(getTextWidth(text, font)) + (hasFocus ? dropdownAppearanceAndPaddingWidth : 0);
  element.style.width = width + "px";
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
        const fieldRef = useRef<HTMLSelectElement>();
        useEffect(() => {
          adjustDropdownWidthToContent(fieldRef.current);
          changeLanguage(values.language);
        }, [values]);
        useEffect(() => {
          adjustDropdownWidthToContent(fieldRef.current);
        }, [focus]);
        return <div>
          <Form className="pure-form">
            <label>
              <FontAwesomeIcon icon={faLanguage}
                               title={t('Navigation.changeLanguage')}
                               className={styles.languageSelectionIcon}/>
              {' '}
              <Field name="language"
                     id="language-selection"
                     component="select"
                     aria-label={t('Navigation.changeLanguage')}
                     title={t('Navigation.changeLanguage')}
                     className={styles.languageSelection}
                     innerRef={fieldRef}
                     onFocus={() => setFocus(true)}
                     onBlur={() => setFocus(false)}>
                {languages.map(({code, englishName, nativeName}) =>
                  <option key={code} value={code}>
                    {values.language === code ?
                      nativeName :
                      formatName(englishName, nativeName)}
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
