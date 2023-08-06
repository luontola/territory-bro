// Copyright © 2015-2023 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import "../layout/defaultStyles";
import {storiesOf} from "@storybook/react";
import {demoCongregation, regionKaivopuisto, regionKatajanokka, territory101} from "../testdata";
import {mapRasters} from "../maps/mapOptions";
import TerritoryCard from "./TerritoryCard";
import NeighborhoodCard from "./NeighborhoodCard";
import RuralTerritoryCard from "./RuralTerritoryCard";
import RegionPrintout from "./RegionPrintout";
import "../i18n.ts";

storiesOf('Printouts', module)
  .add('TerritoryCard', () =>
    <TerritoryCard territory={territory101} congregation={demoCongregation}
                   qrCodeUrl="https://qr.territorybro.com/BNvuFBPOrAE" mapRaster={mapRasters[0]}/>)

  .add('NeighborhoodCard', () =>
    <NeighborhoodCard territory={territory101} congregation={demoCongregation} mapRaster={mapRasters[0]}/>)

  .add('RuralTerritoryCard', () =>
    <RuralTerritoryCard territory={territory101} congregation={demoCongregation}
                        qrCodeUrl="https://qr.territorybro.com/BNvuFBPOrAE" mapRaster={mapRasters[0]}/>)

  .add('RegionPrintout', () =>
    <RegionPrintout region={regionKatajanokka} congregation={demoCongregation} mapRaster={mapRasters[0]}/>)

  .add('RegionPrintout, multi-polygon', () =>
    <RegionPrintout region={regionKaivopuisto} congregation={demoCongregation} mapRaster={mapRasters[0]}/>)

  .add('RegionPrintout, congregation', () =>
    <RegionPrintout region={demoCongregation} congregation={demoCongregation} mapRaster={mapRasters[0]}/>);
