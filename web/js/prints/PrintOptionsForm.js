// Copyright © 2015-2019 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import React from "react";
import {Field, formValueSelector, reduxForm} from "redux-form";
import type {MapRaster} from "../maps/mapOptions";
import {connect} from "react-redux";
import type {Region, Territory} from "../api";
import type {State} from "../reducers";
import take from "lodash-es/take";

export const defaultMapRasterId = 'osmHighDpi';
export const formName = 'printOptions';
const selector = formValueSelector(formName);

let PrintOptionsForm = ({
                          territoriesVisible = false,
                          regionsVisible = false,
                          availableMapRasters,
                          availableRegions,
                          availableTerritories,
                          handleSubmit
                        }: {
  territoriesVisible: boolean,
  regionsVisible: boolean,
  availableMapRasters: Array<MapRaster>,
  availableRegions: Array<Region>,
  availableTerritories: Array<Territory>,
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
  return {
    availableMapRasters: state.config.mapRasters,
    availableRegions: getAvailableRegions(state),
    availableTerritories: getAvailableTerritories(state),
    initialValues: {
      mapRaster: defaultMapRasterId,
      regions: getDefaultRegions(state),
      territories: getDefaultTerritories(state),
    }
  }
}

// map rasters

export function getSelectedMapRaster(state: State): MapRaster {
  const id = selector(state, 'mapRaster') || defaultMapRasterId;
  const mapRaster = state.config.mapRasters.find(map => map.id === id);
  if (!mapRaster) {
    throw new Error(`MapRaster not found: ${id}`);
  }
  return mapRaster;
}

// regions

function getAvailableRegions(state: State): Array<Region> {
  return state.api.regions.filter(r => r.congregation || r.subregion);
}

function getDefaultRegions(state: State): Array<number> {
  const regions = getAvailableRegions(state);
  return take(regions, 1).map(r => r.id);
}

export function getSelectedRegions(state: State): Array<Region> {
  const formValues = selector(state, 'regions') || getDefaultRegions(state);
  const ids = new Set(formValues.map(str => parseInt(str, 10)));
  return state.api.regions.filter(r => ids.has(r.id));
}

// territories

function getAvailableTerritories(state: State): Array<Territory> {
  return state.api.territories;
}

function getDefaultTerritories(state: State): Array<number> {
  const territories = getAvailableTerritories(state);
  return take(territories, 1).map(t => t.id);
}

export function getSelectedTerritories(state: State): Array<Territory> {
  const formValues = selector(state, 'territories') || getDefaultTerritories(state);
  const ids = new Set(formValues.map(str => parseInt(str, 10)));
  return state.api.territories.filter(r => ids.has(r.id));
}
