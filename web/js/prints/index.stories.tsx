// Copyright © 2015-2019 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import "../layout/defaultStyles";
import React from "react";
import {storiesOf} from "@storybook/react";
import {demoCongregation, subregionKaivopuisto, subregionKatajanokka, territory101} from "../testdata";
import {mapRasters} from "../maps/mapOptions";
import TerritoryCard from "./TerritoryCard";
import NeighborhoodCard from "./NeighborhoodCard";
import RuralTerritoryCard from "./RuralTerritoryCard";
import {language, messages} from "../intl";
import {IntlProvider} from "react-intl";
import RegionPrintout from "./RegionPrintout";

const Box = ({children}) => <IntlProvider locale={language} messages={messages}>
  {children}
</IntlProvider>;

storiesOf('Printouts', module)
  .add('TerritoryCard', () =>
    <Box>
      <TerritoryCard territory={territory101} congregation={demoCongregation} mapRaster={mapRasters[0]}/>
    </Box>)

  .add('NeighborhoodCard', () =>
    <Box>
      <NeighborhoodCard territory={territory101} congregation={demoCongregation} mapRaster={mapRasters[0]}/>
    </Box>)

  .add('RuralTerritoryCard', () =>
    <Box>
      <RuralTerritoryCard territory={territory101} congregation={demoCongregation} mapRaster={mapRasters[0]}/>
    </Box>)

  .add('RegionPrintout', () =>
    <Box>
      <RegionPrintout region={subregionKatajanokka} congregation={demoCongregation} mapRaster={mapRasters[0]}/>
    </Box>)

  .add('RegionPrintout, multi-polygon', () =>
    <Box>
      <RegionPrintout region={subregionKaivopuisto} congregation={demoCongregation} mapRaster={mapRasters[0]}/>
    </Box>)

  .add('RegionPrintout, congregation', () =>
    <Box>
      <RegionPrintout region={demoCongregation} congregation={demoCongregation} mapRaster={mapRasters[0]}/>
    </Box>);
