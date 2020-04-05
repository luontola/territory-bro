// Copyright © 2015-2019 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import "../layout/defaultStyles";
import React from "react";
import {storiesOf} from "@storybook/react";
import TerritoryMiniMap from "./TerritoryMiniMap";
import {demoCongregation, territory101} from "../testdata";

const Box = ({
  children
}) => <div style={{ width: '5cm', height: '5cm' }}>
    {children}
  </div>;

storiesOf('TerritoryMiniMap', module).add('default', () => <Box>
      <TerritoryMiniMap territory={{
    location: territory101.location,
    enclosingSubregion: territory101.enclosingSubregion,
    enclosingMinimapViewport: territory101.enclosingMinimapViewport
  }} congregation={{
    location: demoCongregation.location
  }} />
    </Box>).add('without subregion', () => <Box>
      <TerritoryMiniMap territory={{
    location: territory101.location,
    enclosingSubregion: null,
    enclosingMinimapViewport: territory101.enclosingMinimapViewport
  }} congregation={{
    location: demoCongregation.location
  }} />
    </Box>).add('without viewport', () => <Box>
      <TerritoryMiniMap territory={{
    location: territory101.location,
    enclosingSubregion: territory101.enclosingSubregion,
    enclosingMinimapViewport: null
  }} congregation={{
    location: demoCongregation.location
  }} />
    </Box>).add('without congregation', () => <Box>
      <TerritoryMiniMap territory={{
    location: territory101.location,
    enclosingSubregion: territory101.enclosingSubregion,
    enclosingMinimapViewport: territory101.enclosingMinimapViewport
  }} congregation={{
    location: null
  }} />
    </Box>);