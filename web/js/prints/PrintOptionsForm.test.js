// Copyright © 2015-2017 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import {describe, it} from "mocha";
import {expect} from "chai";
import {defaultMapRasterId, formName, getSelectedMapRaster} from "./PrintOptionsForm";
import reducers from "../reducers";
import createStore from "redux/lib/createStore";
import {mapRastersLoaded} from "../configActions";
import {change} from "redux-form/lib/actions";

describe("PrintOptionsForm", () => {

  describe("#getSelectedMapRaster", () => {
    const defaultRaster = {id: defaultMapRasterId, name: "", source: null};
    const otherRaster = {id: 'other', name: "", source: null};
    const mapRasters = [defaultRaster, otherRaster];

    it("default value", () => {
      const store = createStore(reducers);
      store.dispatch(mapRastersLoaded(mapRasters));

      expect(getSelectedMapRaster(store.getState())).to.eql(defaultRaster);
    });

    it("changed value", () => {
      const store = createStore(reducers);
      store.dispatch(mapRastersLoaded(mapRasters));
      store.dispatch(change(formName, 'mapRaster', otherRaster.id));

      expect(getSelectedMapRaster(store.getState())).to.eql(otherRaster);
    });
  });
});
