// Copyright © 2015-2021 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

// XXX: in a different file, because Mocha fails to load OpenLayers dependency - will need to run tests in a browser instead of Node.jS
export function sanitizeReturnPath(path, baseUrl = document.location.href) {
  const target = new URL(path, baseUrl);
  const base = new URL(baseUrl);
  if (target.origin === base.origin) {
    return target.pathname + target.search + target.hash;
  } else {
    return "/";
  }
}
