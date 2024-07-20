// Copyright © 2015-2024 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import * as fs from "fs";
import {languages, resources} from "./i18n.ts";
import path from "path";
import {mapRasters} from "./maps/mapOptions.ts";
import {pick} from "lodash-es";

function exportFile(filename, data) {
  const file = "target/web-dist/" + filename;
  fs.mkdirSync(path.dirname(file), {recursive: true})
  fs.writeFileSync(file, JSON.stringify(data, null, 2))
  console.log("Wrote " + file)
}

exportFile("i18n.json",
  {languages, resources}
);

exportFile("map-rasters.json",
  mapRasters.map(raster => pick(raster, ["id", "name"]))
);
