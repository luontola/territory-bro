// Copyright © 2015-2019 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import React from 'react';
import {storiesOf} from '@storybook/react';
import {territory101, territory102} from "./testdata";
import TerritoryMap from "./TerritoryMap";
import {mapRasters} from "./mapOptions";

const Box = ({children}) => (
  <div style={{width: '15cm', height: '10cm'}}>
    {children}
  </div>
);

const stories = storiesOf('TerritoryMap', module)
  .add('default', () =>
    <Box>
      <TerritoryMap
        territory={territory101}
        mapRaster={mapRasters[0]}/>
    </Box>)

  .add('multi-polygon', () =>
    <Box>
      <TerritoryMap
        territory={territory102}
        mapRaster={mapRasters[0]}/>
    </Box>);

mapRasters.forEach(mapRaster => {
  stories.add(`with ${mapRaster.id} raster`, () =>
    <Box>
      <TerritoryMap
        territory={territory101}
        mapRaster={mapRaster}/>
    </Box>)
});
