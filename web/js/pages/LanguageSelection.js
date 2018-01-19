// Copyright © 2015-2018 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import React from "react";
import {changeLanguage} from "../intl";

const LanguageSelection = () => (
  <p>Change language:
    {' '}<a href="#" onClick={handleLanguageChange('en')}>English</a>
    {' '}<a href="#" onClick={handleLanguageChange('fi')}>Finnish</a>
    {' '}<a href="#" onClick={handleLanguageChange('it')}>Italian</a>
    {' '}<a href="#" onClick={handleLanguageChange('pt')}>Portuguese</a>
    {' '}<a href="#" onClick={handleLanguageChange('es')}>Spanish</a>
  </p>
);

function handleLanguageChange(lang) {
  return (event) => {
    event.preventDefault();
    changeLanguage(lang);
  }
}

export default LanguageSelection;
