// Copyright © 2015-2021 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import {describe, it} from "mocha";
import {expect} from "chai";
import {sanitizeReturnPath} from "./authenticationUtil";

const loginCallbackUrl = "https://beta.territorybro.com/login-callback?return=/";

describe("sanitizeReturnPath", () => {

  it("absolute path", () => {
    expect(sanitizeReturnPath("/page", loginCallbackUrl)).to.eql("/page");
  });

  it("relative path", () => {
    expect(sanitizeReturnPath("page", loginCallbackUrl)).to.eql("/page");
  });

  it("query parameters and hash", () => {
    expect(sanitizeReturnPath("/some/path?some-query#some-hash", loginCallbackUrl)).to.eql("/some/path?some-query#some-hash");
  });

  it("same domain", () => {
    expect(sanitizeReturnPath("https://beta.territorybro.com/page", loginCallbackUrl)).to.eql("/page");
  });

  it("different domain", () => {
    expect(sanitizeReturnPath("https://malicious.example.com/page", loginCallbackUrl)).to.eql("/");
  });

  it("XSS", () => {
    expect(sanitizeReturnPath("javascript:alert(1)", loginCallbackUrl)).to.eql("/");
    expect(sanitizeReturnPath("data:text/html,<script>alert(1)</script>", loginCallbackUrl)).to.eql("/");
  });
});
