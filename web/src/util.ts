// Copyright © 2015-2022 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import axios from "axios";
import {useState} from "react";

const api = axios.create({
  timeout: 15000,
  headers: {
    'Accept': 'application/json'
  }
});
export {api};

export function getPageState(key) {
  return window.history.state?.[key];
}

export function setPageState(key, value) {
  const newState = {
    ...window.history.state,
    [key]: value
  };
  window.history.replaceState(newState, '')
}

export function usePageState(key, initialState) {
  const [value, setValue] = useState(getPageState(key) || initialState);
  return [value, newValue => {
    setValue(newValue);
    setPageState(key, newValue)
  }];
}
