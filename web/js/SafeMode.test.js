// Copyright © 2015-2017 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import {beforeEach, describe, it} from "mocha";
import {expect} from "chai";
import SafeMode from "./SafeMode";
import Cookies from "js-cookie";

Cookies.set('name', 'value');
//expect(Cookies.get()).to.eql('value');

describe("SafeMode", () => {

  beforeEach(() => {
    new SafeMode().loadedSuccessfully();
  });

  it("starts in normal mode", () => {
    expect(new SafeMode().isSafeMode()).to.eql(false);
  });

  it("stays in normal mode if page is loaded successfully", () => {
    new SafeMode().loadedSuccessfully();
    expect(new SafeMode().isSafeMode()).to.eql(false);
  });

  it("on double start without success, enters safe mode", () => {
    // TODO: number of tries as constructor parameter (maybe not needed?)
    new SafeMode();
    expect(new SafeMode().isSafeMode()).to.eql(true);
  });

  // TODO: stays in safe mode when successfully loads in safe mode
  // TODO: given safe mode and successful load, after refresh is no more in safe mode
});
