// Copyright © 2015-2017 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import React from "react";
import {Field, formValueSelector, reduxForm} from "redux-form";
import type {MapRaster} from "../maps/mapOptions";
import {mapRasters} from "../maps/mapOptions";
import {connect} from "react-redux";
import type {Region, Territory} from "../api";
import type {State} from "../reducers";

const defaultMapRasterId = 'osmHighDpi';
const formName = 'printOptions';
const selector = formValueSelector(formName);

let PrintOptionsForm = ({territories, regions, handleSubmit}: {
  territories: Array<Territory>,
  regions: Array<Region>,
  handleSubmit: any
}) => (
  <form onSubmit={handleSubmit}>
    <p><b>Map Raster </b>
      <Field name="mapRaster" component="select">
        {mapRasters.map(map => <option value={map.id} key={map.id}>{map.name}</option>)}
      </Field></p>

    <p><b>Regions</b>
      <br/>
      <Field name="regions" component="select" multiple size={7}>
        {regions.map(r => <option key={r.id} value={r.id}>{r.name}</option>)}
      </Field>
    </p>

    <p><b>Territories</b>
      <br/>
      <Field name="territories" component="select" multiple size={7}>
        {territories.map(t => <option key={t.id} value={t.id}>{t.number}</option>)}
      </Field>
    </p>
  </form>
);

PrintOptionsForm = reduxForm({
  form: formName,
  destroyOnUnmount: false,
})(PrintOptionsForm);

PrintOptionsForm = connect(mapStateToProps)(PrintOptionsForm);

export default PrintOptionsForm;

function mapStateToProps(state: State) {
  // TODO: filter territories based on selected region
  return {
    territories: state.api.territories,
    regions: state.api.regions.filter(r => r.congregation || r.subregion),
    initialValues: {
      mapRaster: defaultMapRasterId,
      regions: [],
      territories: [],
    }
  }
}

export function getSelectedMapRaster(state: State): MapRaster {
  const id = selector(state, 'mapRaster') || defaultMapRasterId;
  const mapRaster = mapRasters.find(map => map.id === id);
  if (!mapRaster) {
    throw new Error(`MapRaster not found: ${id}`);
  }
  return mapRaster;
}

export function getSelectedRegions(state: State): Array<Region> {
  const formValues = selector(state, 'regions') || [];
  const ids = new Set(formValues.map(str => parseInt(str, 10)));
  const regions = state.api.regions;
  if (ids.size === 0) {
    return regions;
  } else {
    return regions.filter(r => ids.has(r.id));
  }
}

export function getSelectedTerritories(state: State): Array<Territory> {
  const formValues = selector(state, 'territories') || [];
  const ids = new Set(formValues.map(str => parseInt(str, 10)));
  const territories = state.api.territories;
  if (ids.size === 0) {
    return territories;
  } else {
    return territories.filter(r => ids.has(r.id));
  }
}
