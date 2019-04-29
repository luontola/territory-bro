// Copyright © 2015-2019 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import React from "react";
import {changeLanguage, languagesByCode} from "../intl";
import toPairs from "lodash/toPairs";
import sortBy from "lodash/sortBy";

const sortedLanguages = sortBy(toPairs(languagesByCode), ([code, name]) => name);

const LanguageSelection = () => (
  <React.Fragment>
    <p>Change language:</p>
    <ul>
      {sortedLanguages.map(([code, name]) =>
        <li key={code}><a href="#" onClick={handleLanguageChange(code)}>{name}</a></li>)}
    </ul>
  </React.Fragment>
);

function handleLanguageChange(lang) {
  return (event) => {
    event.preventDefault();
    changeLanguage(lang);
  }
}

export default LanguageSelection;
