// Copyright © 2015-2023 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import {describe, it} from "vitest";
import {expect} from "chai";
import {languages, resources} from "./i18n.ts";

describe("i18n", () => {
  it("translations and the list of languages are in sync", () => {
    const listedLanguages = languages.map(lang => lang.code).sort();
    const availableTranslations = Object.keys(resources).sort();
    expect(listedLanguages).to.eql(availableTranslations);
  });
});
