// Copyright © 2015-2020 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import "../layout/defaultStyles";
import React from "react";
import {storiesOf} from "@storybook/react";
import TerritoryMiniMap from "./TerritoryMiniMap";
import {demoCongregation, territory101} from "../testdata";
import {Congregation, Territory} from "../api";

const Box = ({children}) =>
  <div style={{width: '5cm', height: '5cm'}}>
    {children}
  </div>;

storiesOf('TerritoryMiniMap', module)
  .add('default', () =>
    <Box>
      <TerritoryMiniMap
        territory={{
          location: territory101.location,
          enclosingRegion: territory101.enclosingRegion,
          enclosingMinimapViewport: territory101.enclosingMinimapViewport
        } as Territory}
        congregation={{
          location: demoCongregation.location
        } as Congregation}
        printout={true}/>
    </Box>)

  .add('without region', () =>
    <Box>
      <TerritoryMiniMap
        territory={{
          location: territory101.location,
          enclosingRegion: null,
          enclosingMinimapViewport: territory101.enclosingMinimapViewport
        } as Territory}
        congregation={{
          location: demoCongregation.location
        } as Congregation}
        printout={true}/>
    </Box>)

  .add('without viewport', () =>
    <Box>
      <TerritoryMiniMap
        territory={{
          location: territory101.location,
          enclosingRegion: territory101.enclosingRegion,
          enclosingMinimapViewport: null
        } as Territory}
        congregation={{
          location: demoCongregation.location
        } as Congregation}
        printout={true}/>
    </Box>)

  .add('without congregation', () =>
    <Box>
      <TerritoryMiniMap
        territory={{
          location: territory101.location,
          enclosingRegion: territory101.enclosingRegion,
          enclosingMinimapViewport: territory101.enclosingMinimapViewport
        } as Territory}
        congregation={{
          location: null
        } as Congregation}
        printout={true}/>
    </Box>);
