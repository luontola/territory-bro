// Copyright © 2015-2017 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import {beforeEach, describe, it} from "mocha";
import {expect} from "chai";
import {
  defaultMapRasterId,
  formName,
  getSelectedMapRaster,
  getSelectedRegions,
  getSelectedTerritories
} from "./PrintOptionsForm";
import reducers from "../reducers";
import createStore from "redux/lib/createStore";
import {mapRastersLoaded} from "../configActions";
import {change} from "redux-form/lib/actions";
import {regionsLoaded, territoriesLoaded} from "../apiActions";
import type {Region, Territory} from "../api";
import type {MapRaster} from "../maps/mapOptions";

describe("PrintOptionsForm", () => {
  let store;
  beforeEach(() => {
    store = createStore(reducers);
  });

  describe("#getSelectedMapRaster", () => {
    const defaultRaster = rasterStub({id: defaultMapRasterId});
    const otherRaster = rasterStub({id: 'other'});
    const mapRasters = [defaultRaster, otherRaster];
    beforeEach(() => {
      store.dispatch(mapRastersLoaded(mapRasters));
    });

    it("defaults to default raster", () => {
      expect(getSelectedMapRaster(store.getState())).to.eql(defaultRaster);
    });

    it("changes with form selection", () => {
      store.dispatch(change(formName, 'mapRaster', otherRaster.id));
      expect(getSelectedMapRaster(store.getState())).to.eql(otherRaster);
    });
  });

  describe("#getSelectedRegions", () => {
    const region1 = regionStub({id: 1});
    const region2 = regionStub({id: 2});
    const region3 = regionStub({id: 3});
    const regions = [region1, region2, region3];
    beforeEach(() => {
      store.dispatch(regionsLoaded(regions));
    });

    it("defaults to all regions", () => {
      expect(getSelectedRegions(store.getState())).to.eql(regions);
    });

    it("changes with form selection", () => {
      store.dispatch(change(formName, 'regions', ['1', '3']));
      expect(getSelectedRegions(store.getState())).to.eql([region1, region3]);
    });

    it("empty selection implies all regions", () => {
      store.dispatch(change(formName, 'regions', []));
      expect(getSelectedRegions(store.getState())).to.eql(regions);
    });
  });

  describe("#getSelectedTerritories", () => {
    const territory1 = territoryStub({id: 1});
    const territory2 = territoryStub({id: 2});
    const territory3 = territoryStub({id: 3});
    const territories = [territory1, territory2, territory3];
    beforeEach(() => {
      store.dispatch(territoriesLoaded(territories));
    });

    it("defaults to all territories", () => {
      expect(getSelectedTerritories(store.getState())).to.eql(territories);
    });

    it("changes with form selection", () => {
      store.dispatch(change(formName, 'territories', ['1', '3']));
      expect(getSelectedTerritories(store.getState())).to.eql([territory1, territory3]);
    });

    it("empty selection implies all territories", () => {
      store.dispatch(change(formName, 'territories', []));
      expect(getSelectedTerritories(store.getState())).to.eql(territories);
    });
  });
});

function rasterStub(mapRaster: any): MapRaster {
  return mapRaster;
}

function regionStub(region: any): Region {
  return region;
}

function territoryStub(territory: any): Territory {
  return territory;
}
