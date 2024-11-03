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
