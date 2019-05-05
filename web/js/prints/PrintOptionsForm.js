// Copyright © 2015-2019 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import React from "react";
import {Field, Form, Formik} from "formik";
import {mapRasters} from "../maps/mapOptions";
import {getCongregationById} from "../api";
import TerritoryCard from "./TerritoryCard";
import NeighborhoodCard from "./NeighborhoodCard";
import RuralTerritoryCard from "./RuralTerritoryCard";
import RegionPrintout from "./RegionPrintout";

const templates = [
  {
    id: 'TerritoryCard',
    component: TerritoryCard,
    name: 'Territory Card',
    type: 'territory',
  },
  {
    id: 'NeighborhoodCard',
    component: NeighborhoodCard,
    name: 'Neighborhood Map',
    type: 'territory',
  },
  {
    id: 'RuralTerritoryCard',
    component: RuralTerritoryCard,
    name: 'Rural Territory Card',
    type: 'territory',
  },
  {
    id: 'RegionPrintout',
    component: RegionPrintout,
    name: 'Subregion Map',
    type: 'region',
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
            const territory = availableTerritories.find(t => t.id === territoryId);
            const mapRasterId = values.mapRaster;
            const mapRaster = availableMapRasters.find(r => r.id === mapRasterId);
            return template.type === 'territory' &&
              <template.component key={territory.id}
                                  congregationId={congregationId}
                                  territoryId={territory.id}
                                  territory={territory}
                                  mapRaster={mapRaster}/>;
          }
        )}
        {values.regions.map(regionId => {
          const template = templates.find(t => t.id === values.template);
          const region = availableRegions.find(t => t.id === regionId);
          const mapRasterId = values.mapRaster;
          const mapRaster = availableMapRasters.find(r => r.id === mapRasterId);
          return template.type === 'region' &&
            <template.component key={region.id}
                                congregationId={congregationId}
                                regionId={region.id}
                                region={region}
                                territories={availableTerritories}
                                mapRaster={mapRaster}/>;
        })}
      </>)}
    </Formik>
  );
};

export default PrintOptionsForm;
