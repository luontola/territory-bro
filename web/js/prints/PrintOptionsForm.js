// Copyright © 2015-2017 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import React from "react";
import {Field, formValueSelector, reduxForm} from "redux-form";
import type {MapRaster} from "../maps/mapOptions";
import {connect} from "react-redux";
import type {Region, Territory} from "../api";
import type {State} from "../reducers";

export const defaultMapRasterId = 'osmHighDpi';
export const formName = 'printOptions';
const selector = formValueSelector(formName);

let PrintOptionsForm = ({
                          territoriesVisible = false,
                          regionsVisible = false,
                          availableMapRasters,
                          availableTerritories,
                          availableRegions,
                          handleSubmit
                        }: {
  territoriesVisible: boolean,
  regionsVisible: boolean,
  availableMapRasters: Array<MapRaster>,
  availableTerritories: Array<Territory>,
  availableRegions: Array<Region>,
  handleSubmit: any
}) => (
  <form onSubmit={handleSubmit} className="pure-form pure-form-stacked">
    <fieldset>
      <legend>Print Options</legend>
      <div className="pure-g">

        <div className="pure-u-1 pure-u-md-1-3">
          <label htmlFor="mapRaster">Map Raster</label>
          <Field id="mapRaster" name="mapRaster" component="select" className="pure-input-1">
            {availableMapRasters.map(mapRaster =>
              <option key={mapRaster.id} value={mapRaster.id}>{mapRaster.name}</option>)}
          </Field>
        </div>

        {regionsVisible &&
        <div className="pure-u-1 pure-u-md-1-3">
          <label htmlFor="regions">Regions</label>
          <Field id="regions" name="regions" component="select" multiple size={7} className="pure-input-1">
            {availableRegions.map(region =>
              <option key={region.id} value={region.id}>{region.name}</option>)}
          </Field>
        </div>
        }

        {territoriesVisible &&
        <div className="pure-u-1 pure-u-md-1-3">
          <label htmlFor="territories">Territories</label>
          <Field id="territories" name="territories" component="select" multiple size={7} className="pure-input-1">
            {availableTerritories.map(territory =>
              <option key={territory.id} value={territory.id}>
                {territory.region ? `${territory.number} - ${territory.region}` : territory.number}
              </option>)}
          </Field>
        </div>
        }

      </div>
    </fieldset>
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
    availableMapRasters: state.config.mapRasters,
    availableTerritories: state.api.territories,
    availableRegions: state.api.regions.filter(r => r.congregation || r.subregion),
    initialValues: {
      mapRaster: defaultMapRasterId,
      regions: [],
      territories: [],
    }
  }
}

export function getSelectedMapRaster(state: State): MapRaster {
  const id = selector(state, 'mapRaster') || defaultMapRasterId;
  const mapRaster = state.config.mapRasters.find(map => map.id === id);
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
