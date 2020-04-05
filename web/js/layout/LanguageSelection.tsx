// Copyright © 2015-2019 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0


import React from "react";
import {changeLanguage, languages} from "../intl";

const LanguageSelection = () => <React.Fragment>
    <p>Change language:</p>
    <ul>
      {languages.map(({
      code,
      name
    }) => <li key={code}><a href="#" onClick={handleLanguageChange(code)}>{name}</a></li>)}
    </ul>
  </React.Fragment>;

function handleLanguageChange(lang) {
  return event => {
    event.preventDefault();
    changeLanguage(lang);
  };
}

export default LanguageSelection;