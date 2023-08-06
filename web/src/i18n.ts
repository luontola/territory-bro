// Copyright © 2015-2023 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import i18n from 'i18next';
import {initReactI18next} from 'react-i18next';
import LanguageDetector from 'i18next-browser-languagedetector';
import resources from 'virtual:i18next-loader';

export {resources};

export const languages = [
  {code: "en", englishName: "English", nativeName: "English"},
  {code: "es", englishName: "Spanish", nativeName: "español"},
  {code: "fi", englishName: "Finnish", nativeName: "suomi"},
  {code: "id", englishName: "Indonesian", nativeName: "Indonesia"},
  {code: "it", englishName: "Italian", nativeName: "Italiano"},
  {code: "nl", englishName: "Dutch", nativeName: "Nederlands"},
  {code: "pt", englishName: "Portuguese", nativeName: "Português"},
].sort((a, b) => a.nativeName.localeCompare(b.nativeName, undefined, {sensitivity: 'base'}))

i18n.use(LanguageDetector)
  .use(initReactI18next)
  // https://www.i18next.com/overview/configuration-options
  .init({
    resources,
    fallbackLng: 'en',
    debug: import.meta.env.DEV,
    interpolation: {
      escapeValue: false // React already escapes by default
    }
  });

export async function changeLanguage(language: string) {
  await i18n.changeLanguage(language);
  // TODO: set the lang for document element after the whole application has been translated
  //document.documentElement.setAttribute('lang', i18n.language);
}

export default i18n;
