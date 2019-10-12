// Copyright © 2015-2019 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import {describe, it} from "mocha";
import {expect} from "chai";
import {formatApiError} from "./errorMessages";

describe("formatApiError", () => {

  it("error code: no-such-user", () => {
    const error = {response: {data: {errors: [["no-such-user", "89f8bcde-fa65-47cb-b5cc-9b11d01e410d"]]}}};
    expect(formatApiError(error)).to.eql("User does not exist: 89f8bcde-fa65-47cb-b5cc-9b11d01e410d");
  });

  it("unrecognized error code is passed through", () => {
    const error = {response: {data: {errors: [["foo", 42]]}}};
    expect(formatApiError(error)).to.eql('["foo",42]');
  });

  it("unrecognized format is passed through", () => {
    const error = "foo";
    expect(formatApiError(error)).to.eql(error);
  });

  it("multiple errors are joined by newline", () => {
    const error = {response: {data: {errors: [["foo"], ["bar"]]}}};
    expect(formatApiError(error)).to.eql('["foo"]\n["bar"]');
  });
});
