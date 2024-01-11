// Copyright © 2015-2024 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import * as fs from "fs";
import "./layout/defaultStyles";
import A4PrintFrame from "./prints/A4PrintFrame.module.css";
import A5PrintFrame from "./prints/A5PrintFrame.module.css";
import CongregationSettings from "./pages/CongregationSettings.module.css";
import CropMarks from "./prints/CropMarks.module.css";
import InfoBox from "./maps/InfoBox.module.css";
import Layout from "./layout/Layout.module.css";
import NeighborhoodCard from "./prints/NeighborhoodCard.module.css";
import OpenLayersMap from "./maps/OpenLayersMap.module.css";
import PrintDateNotice from "./prints/PrintDateNotice.module.css";
import printout from "./prints/printout.module.css";
import QrCodeOnly from "./prints/QrCodeOnly.module.css";
import RegionPrintout from "./prints/RegionPrintout.module.css";
import RuralTerritoryCard from "./prints/RuralTerritoryCard.module.css";
import SettingsPage from "./pages/SettingsPage.module.css";
import ShowMyLocation from "./maps/ShowMyLocation.module.css";
import TerritoryCard from "./prints/TerritoryCard.module.css";
import TerritoryCardMapOnly from "./prints/TerritoryCardMapOnly.module.css";
import TerritoryListPage from "./pages/TerritoryListPage.module.css";
import TerritoryPage from "./pages/TerritoryPage.module.css";
import UserManagement from "./pages/UserManagement.module.css";

const cssModules = {
  A4PrintFrame,
  A5PrintFrame,
  CongregationSettings,
  CropMarks,
  InfoBox,
  Layout,
  NeighborhoodCard,
  OpenLayersMap,
  PrintDateNotice,
  printout,
  QrCodeOnly,
  RegionPrintout,
  RuralTerritoryCard,
  SettingsPage,
  ShowMyLocation,
  TerritoryCard,
  TerritoryCardMapOnly,
  TerritoryListPage,
  TerritoryPage,
  UserManagement,
};

const file = "target/web-dist/css-modules.json";
fs.writeFileSync(file, JSON.stringify(cssModules, null, 2))
console.log("Wrote " + file)
