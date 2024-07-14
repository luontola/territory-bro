// Copyright © 2015-2024 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import i18n from 'i18next';
import {initReactI18next} from 'react-i18next';
import LanguageDetector from 'i18next-browser-languagedetector';
import resources from 'virtual:i18next-loader';

export {resources};

export const languages = Object.entries(resources)
  .map(([code, resource]) => ({
    code: code,
    englishName: resource.translation.englishName as string,
    nativeName: resource.translation.nativeName as string,
  }))
  .sort((a, b) => a.nativeName.localeCompare(b.nativeName, undefined, {sensitivity: 'base'}))

const options = {
  resources,
  fallbackLng: 'en',
  // debug: import.meta.env.DEV,
  interpolation: {
    escapeValue: false // React already escapes by default
  },
  react: {
    transKeepBasicHtmlNodesFor: ['p', 'br', 'strong', 'em', 'i', 'u', 'b', 'kbd', 'wbr']
  }
};

i18n.use(LanguageDetector)
  .use(initReactI18next)
  // https://www.i18next.com/overview/configuration-options
  .init(options)
  .then(() => {
    setDocumentLanguage();
  });

function setDocumentLanguage() {
  if (typeof document !== 'undefined') { // avoid crash when running in Node.js
    document.documentElement.setAttribute('lang', i18n.resolvedLanguage);
  }
}

export default i18n;
