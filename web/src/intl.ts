// Copyright © 2015-2020 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import Cookies from "js-cookie";
import translations from "./translations.json";
import sortBy from "lodash/sortBy";
import toPairs from "lodash/toPairs";
import flatMap from "lodash/flatMap";

// TODO: create translations.json at build time, store the english version only in JSX tags to avoid getting out of sync
// https://medium.freecodecamp.com/internationalization-in-react-7264738274a0
// https://github.com/iam-peekay/inbox-react-intl/issues/1

const languagesByCode = {
  en: "English",
  es: "Spanish",
  fi: "Finnish",
  id: "Indonesian",
  it: "Italian",
  nl: "Dutch",
  pt: "Portuguese"
};

export const languages = sortBy(toPairs(languagesByCode).map(([code, name]) => ({code, name})), ({name}) => name);

export function getMessages(language: string): {} {
  return translations[language];
}

function withoutRegionCode(language: string): string {
  return language && language.toLowerCase().split(/[_-]+/)[0];
}

export function changeLanguage(language: string): void {
  Cookies.set('lang', language, {expires: 3 * 365});
  window.location.reload();
}

const languagePreference: string[] = flatMap([
    Cookies.get('lang'),
    ...(navigator.languages || []),
    navigator.language,
    (navigator as any).userLanguage, // Internet Explorer
  ],
  lang => [lang, withoutRegionCode(lang)]);

export const language: string = languagePreference.find(getMessages) || 'en';

export const messages: {} = getMessages(language);
