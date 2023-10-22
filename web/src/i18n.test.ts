// Copyright © 2015-2023 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import {describe, it} from "vitest";
import {expect} from "chai";
import i18n, {languages} from "./i18n.ts";
import readme from "./locales/README.md?raw";
import {sortBy} from "lodash-es";

describe("i18n", () => {
  describe("all languages contain the same translation keys", () => {
    const englishData = i18n.getDataByLanguage("en");
    const englishKeys = getAllKeyPaths(englishData).sort();
    languages.forEach(language => {
      it(`${language.code} ${language.englishName}`, () => {
        const data = i18n.getDataByLanguage(language.code);
        const keys = getAllKeyPaths(data).sort();
        expect(keys).to.eql(englishKeys)
      });
    })
  });

  it("the supported languages are listed in README for easy copying to GPT prompts", () => {
    const languageList = sortBy(languages, ['code'])
      .map(lang => `${lang.englishName} (${lang.code})`)
      .join(", ");
    expect(readme).to.contain(languageList, languageList);
  });

  it("getAllKeyPaths helper", () => {
    const inputObject = {
      obj1: {
        obj2: {
          data1: 1,
          data2: "2",
          obj3: {
            data: "x",
          },
        },
      },
      obj4: {
        description: "y",
      },
    };
    const keyPaths = getAllKeyPaths(inputObject).sort();
    expect(keyPaths).to.eql([
      "obj1.obj2.data1",
      "obj1.obj2.data2",
      "obj1.obj2.obj3.data",
      "obj4.description",
    ]);
  });
});

function getAllKeyPaths(obj, prefix = "") {
  const keyPaths = [];
  for (const key in obj) {
    if (obj.hasOwnProperty(key)) {
      const currentKeyPath = prefix ? `${prefix}.${key}` : key;
      if (typeof obj[key] === "object" && obj[key] !== null) {
        keyPaths.push(...getAllKeyPaths(obj[key], currentKeyPath));
      } else {
        keyPaths.push(currentKeyPath);
      }
    }
  }
  return keyPaths;
}
