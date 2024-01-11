// Copyright © 2015-2024 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import * as fs from "fs";
import {languages, resources} from "./i18n.ts";
import path from "path";

const i18n = {
  languages,
  resources
};

const file = "target/web-dist/i18n.json";
fs.mkdirSync(path.dirname(file), {recursive: true})
fs.writeFileSync(file, JSON.stringify(i18n, null, 2))
console.log("Wrote " + file)
