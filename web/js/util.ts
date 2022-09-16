// Copyright © 2015-2022 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import axios from "axios";
import {useState} from "react";
import {globalHistory} from "@reach/router";

const api = axios.create({
  timeout: 15000,
  headers: {
    'Accept': 'application/json'
  }
});
export {api};

export function getPageState(key) {
  return globalHistory.location.state?.[key];
}

export function setPageState(key, value) {
  const newState = {
    ...globalHistory.location.state,
    [key]: value
  };
  globalHistory.navigate("", {state: newState, replace: true})
}

export function usePageState(key, initialState) {
  const [value, setValue] = useState(getPageState(key) || initialState);
  return [value, newValue => {
    setValue(newValue);
    setPageState(key, newValue)
  }];
}
