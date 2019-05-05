// Copyright © 2015-2019 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import Cookies from "js-cookie";
import {addLocaleData} from "react-intl";
import en from "react-intl/locale-data/en";
import es from "react-intl/locale-data/es";
import fi from "react-intl/locale-data/fi";
import id from "react-intl/locale-data/id";
import it from "react-intl/locale-data/it";
import nl from "react-intl/locale-data/nl";
import pt from "react-intl/locale-data/pt";
import translations from "./translations.json";
import sortBy from "lodash/sortBy";
import toPairs from "lodash/toPairs";

// TODO: create translations.json at build time, store the english version only in JSX tags to avoid getting out of sync
// https://medium.freecodecamp.com/internationalization-in-react-7264738274a0
// https://github.com/iam-peekay/inbox-react-intl/issues/1

addLocaleData([...en, ...es, ...fi, ...id, ...it, ...nl, ...pt]);

const languagesByCode = {
  en: "English",
  es: "Spanish",
  fi: "Finnish",
  id: "Indonesian",
  it: "Italian",
  nl: "Dutch",
  pt: "Portuguese",
};

export const languages =
  sortBy(
    toPairs(languagesByCode).map(([code, name]) => ({code, name})),
    ({name}) => name);

export function getMessages(language: string): {} {
  return translations[language] || translations[withoutRegionCode(language)];
}

function withoutRegionCode(language: string): string {
  return language && language.toLowerCase().split(/[_-]+/)[0]
}

export function changeLanguage(language: string): void {
  Cookies.set('lang', language, {expires: 3 * 365});
  window.location.reload();
}

const languagePreference: string[] = [
  Cookies.get('lang'),
  ...(navigator.languages || []),
  navigator.language,
  (navigator: any).userLanguage, // Internet Explorer
];

export const language: string = languagePreference.find(getMessages) || 'en';

export const messages: {} = getMessages(language);
