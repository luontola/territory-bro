// Copyright © 2015-2017 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import Cookies from "js-cookie";

let counter = 0;
const cookie = Cookies.get('SafeMode.counter', {expires: 1});
console.log("cookie", cookie);
if (cookie) {
  counter = parseInt(cookie);
}

function incrementAndGetCounter(): number {
  counter++;
  setCookie();
  return counter;
}

function resetCounter() {
  counter = 0;
  setCookie();
}

function setCookie() {
  Cookies.get('SafeMode.counter', counter, {expires: 1})
}

export default class SafeMode {
  count: number;

  constructor() {
    this.count = incrementAndGetCounter();
  }

  loadedSuccessfully() {
    resetCounter();
  }

  isSafeMode() {
    return this.count > 1;
  }
}
