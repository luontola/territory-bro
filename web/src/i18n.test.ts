import {describe, it} from "vitest";
import {expect} from "chai";
import i18n, {languages} from "./i18n.ts";
import readme from "./locales/README.md?raw";
import {sortBy} from "lodash-es";

describe("i18n", () => {
  describe("all languages contain the same translation keys", () => {
    const englishData = i18n.getDataByLanguage("en")?.translation;
    const englishKeys = getAllKeyPaths(englishData).sort();
    expect(englishKeys.length).to.be.greaterThan(100);

    languages.forEach(language => {
      it(`${language.code} ${language.englishName}`, () => {
        if (language.code === 'en') {
          return; // don't compare the reference language to itself; it contains notTranslatedKeys
        }
        const data = i18n.getDataByLanguage(language.code)?.translation;
        const keys = getAllKeyPaths(data).sort();
        expect(keys).to.eql(englishKeys)

        const enT = i18n.getFixedT('en');
        const langT = i18n.getFixedT(language.code);
        for (const key of keys) {
          expect(getPlaceholders(langT(key)), `placeholders of ${key}`).to.eql(getPlaceholders(enT(key)))
        }
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
  const keyPaths: string[] = [];
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

function getPlaceholders(s) {
  return [...s.matchAll(/<\/?.+?>|{{.+?}}/g)]
    .map(match => match[0])
    .filter(s => s != "<wbr>") // languages with long words may use the line break opportunity element when needed
    .sort();
}
