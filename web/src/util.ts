// Copyright © 2015-2024 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

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
