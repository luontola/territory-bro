// Copyright © 2015-2023 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import "../layout/defaultStyles";
import {storiesOf} from "@storybook/react";
import {territory101, territory102} from "../testdata";
import TerritoryMap from "./TerritoryMap";
import {mapRasters} from "./mapOptions";

const Box = ({children}) =>
  <div style={{width: '15cm', height: '10cm'}}>
    {children}
  </div>;

const stories = storiesOf('TerritoryMap', module)
  .add('default', () =>
    <Box>
      <TerritoryMap territory={territory101}
                    mapRaster={mapRasters[0]}
                    printout={true}/>
    </Box>)
  .add('multi-polygon', () =>
    <Box>
      <TerritoryMap territory={territory102}
                    mapRaster={mapRasters[0]}
                    printout={true}/>
    </Box>);

mapRasters.forEach(mapRaster => {
  stories.add(`with ${mapRaster.id} raster`, () => {
    let territory = territory101;
    if (mapRaster.id === 'vantaaKaupunkikartta') {
      territory = {
        ...territory,
        location: "MULTIPOLYGON(((25.0319657181808 60.2951399010073,25.0329619630632 60.2951063504953,25.0328265511374 60.2943945922339,25.0323139202756 60.2944185574107,25.0323767900983 60.2948571170425,25.0319270290592 60.2948786853971,25.0319657181808 60.2951399010073)))"
      };
    }
    return <Box>
      <TerritoryMap territory={territory}
                    mapRaster={mapRaster}
                    printout={true}/>
    </Box>;
  });
});
