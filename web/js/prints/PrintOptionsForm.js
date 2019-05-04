// Copyright © 2015-2019 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import React from "react";
import {Field, Form, Formik} from "formik";
import type {MapRaster} from "../maps/mapOptions";
import {mapRasters} from "../maps/mapOptions";
import type {Region, Territory} from "../api";
import {getCongregationById} from "../api";
import type {State} from "../reducers";
import take from "lodash/take";
import TerritoryCard from "./TerritoryCard";
import NeighborhoodCard from "./NeighborhoodCard";
import RuralTerritoryCard from "./RuralTerritoryCard";
import RegionPrintout from "./RegionPrintout";

const templates = [
  {
    id: 'TerritoryCard',
    component: TerritoryCard,
    name: 'Territory Card',
  },
  {
    id: 'NeighborhoodCard',
    component: NeighborhoodCard,
    name: 'Neighborhood Map',
  },
  {
    id: 'RuralTerritoryCard',
    component: RuralTerritoryCard,
    name: 'Rural Territory Card',
  },
  {
    id: 'RegionPrintout',
    component: RegionPrintout,
    name: 'Subregion Map',
  },
]

const PrintOptionsForm = ({congregationId, territoriesVisible = false, regionsVisible = false}) => {
  const availableMapRasters = mapRasters;
  const congregation = getCongregationById(congregationId);
  const availableTerritories = congregation.territories;
  const availableRegions = congregation.subregions;

  return (
    <Formik initialValues={{
      template: templates[0].id,
      mapRaster: 'osmHighDpi',
      regions: availableRegions.length > 0 ? [availableRegions[0].id] : [],
      territories: availableTerritories.length > 0 ? [availableTerritories[0].id] : [],
    }}>{({values, setFieldValue}) => (
      <>
        <div className="no-print">
          <Form className="pure-form pure-form-stacked">
            <fieldset>
              <legend>Print Options</legend>
              <div className="pure-g">

                <div className="pure-u-1 pure-u-md-1-3">
                  <label htmlFor="mapRaster">Template</label>
                  <Field name="template" id="template" component="select" className="pure-input-1">
                    {templates.map(template =>
                      <option key={template.id} value={template.id}>{template.name}</option>)}
                  </Field>
                </div>

                <div className="pure-u-1 pure-u-md-1-3">
                  <label htmlFor="mapRaster">Map Raster</label>
                  <Field name="mapRaster" id="mapRaster" component="select" className="pure-input-1">
                    {availableMapRasters.map(mapRaster =>
                      <option key={mapRaster.id} value={mapRaster.id}>{mapRaster.name}</option>)}
                  </Field>
                </div>
              </div>
              <div className="pure-g">
                {regionsVisible &&
                <div className="pure-u-1 pure-u-md-1-3">
                  <label htmlFor="regions">Subregions</label>
                  <Field name="regions" id="regions" component="select" multiple size={7} className="pure-input-1"
                         onChange={event =>
                           setFieldValue(
                             "regions",
                             [].slice
                               .call(event.target.selectedOptions)
                               .map(option => option.value)
                           )
                         }>
                    {availableRegions.map(region =>
                      <option key={region.id} value={region.id}>{region.name}</option>)}
                  </Field>
                </div>
                }

                {territoriesVisible &&
                <div className="pure-u-1 pure-u-md-1-3">
                  <label htmlFor="territories">Territories</label>
                  <Field name="territories" id="territories" component="select" multiple size={7}
                         className="pure-input-1"
                         onChange={event =>
                           setFieldValue(
                             "territories",
                             [].slice
                               .call(event.target.selectedOptions)
                               .map(option => option.value)
                           )
                         }>
                    {availableTerritories.map(territory =>
                      <option key={territory.id} value={territory.id}>
                        {territory.subregion ? `${territory.number} - ${territory.subregion}` : territory.number}
                      </option>)}
                  </Field>
                </div>
                }

              </div>
            </fieldset>
          </Form>
        </div>

        {values.territories.map(territoryId => {
            const template = templates.find(t => t.id === values.template);
            const mapRasterId = values.mapRaster;
            const territory = availableTerritories.find(t => t.id === territoryId);
            const mapRaster = availableMapRasters.find(r => r.id === mapRasterId)
            return <template.component key={territory.id}
                                       congregationId={congregationId}
                                       territoryId={territory.id}
                                       territory={territory}
                                       mapRaster={mapRaster}/>
          }
        )}
      </>)}
    </Formik>
  );
};

export default PrintOptionsForm;

// map rasters

export function getSelectedMapRaster(state: State): MapRaster {
  const id = selector(state, 'mapRaster') || 'osmHighDpi';
  const mapRaster = mapRasters.find(map => map.id === id);
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
