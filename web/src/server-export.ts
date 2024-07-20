// Copyright © 2015-2024 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import * as fs from "fs";
import {languages, resources} from "./i18n.ts";
import path from "path";
import {mapRasters} from "./maps/mapOptions.ts";
import {pick} from "lodash-es";
import {JSDOM} from "jsdom";
import {parseFontAwesomeIcon} from "./font-awesome.ts";
import closeSvg from "@fortawesome/fontawesome-free/svgs/solid/xmark.svg?raw";
import copySvg from "@fortawesome/fontawesome-free/svgs/solid/copy.svg?raw";
import externalLinkSvg from "@fortawesome/fontawesome-free/svgs/solid/up-right-from-square.svg?raw";
import infoSvg from "@fortawesome/fontawesome-free/svgs/solid/circle-info.svg?raw";
import languageSvg from "@fortawesome/fontawesome-free/svgs/solid/language.svg?raw";
import mapLocationSvg from "@fortawesome/fontawesome-free/svgs/solid/map-location-dot.svg?raw";
import shareSvg from "@fortawesome/fontawesome-free/svgs/solid/share-nodes.svg?raw";
import userSvg from "@fortawesome/fontawesome-free/svgs/solid/user-large.svg?raw";

function exportFile(filename: string, content: string) {
  const file = "target/web-dist/" + filename;
  fs.mkdirSync(path.dirname(file), {recursive: true})
  fs.writeFileSync(file, content)
  console.log("Wrote " + file)
}

function exportJsonFile(filename: string, data: any) {
  exportFile(filename, JSON.stringify(data, null, 2));
}

exportJsonFile("i18n.json",
  {languages, resources}
);

exportJsonFile("map-rasters.json",
  mapRasters.map(raster => pick(raster, ["id", "name"]))
);

global.DOMParser = new JSDOM().window.DOMParser;  // XXX: workaround to make parseFontAwesomeIcon work on Node.js
fs.rmSync("target/web-dist/icons", {recursive: true, force: true});
exportFile("icons/close.svg", parseFontAwesomeIcon(closeSvg).outerHTML);
exportFile("icons/copy.svg", parseFontAwesomeIcon(copySvg).outerHTML);
exportFile("icons/external-link.svg", parseFontAwesomeIcon(externalLinkSvg).outerHTML);
exportFile("icons/info.svg", parseFontAwesomeIcon(infoSvg).outerHTML);
exportFile("icons/language.svg", parseFontAwesomeIcon(languageSvg).outerHTML);
exportFile("icons/map-location.svg", parseFontAwesomeIcon(mapLocationSvg).outerHTML);
exportFile("icons/share.svg", parseFontAwesomeIcon(shareSvg).outerHTML);
exportFile("icons/user.svg", parseFontAwesomeIcon(userSvg).outerHTML);
