// Copyright © 2015-2020 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import React from "react";
import {Field, Form, Formik} from "formik";
import {getMessages, language as defaultLanguage, languages} from "../intl";
import {IntlProvider} from "react-intl";
import {mapRasters} from "../maps/mapOptions";
import {getCongregationById} from "../api";
import TerritoryCard from "./TerritoryCard";
import NeighborhoodCard from "./NeighborhoodCard";
import RuralTerritoryCard from "./RuralTerritoryCard";
import RegionPrintout from "./RegionPrintout";
import TerritoryCardMapOnly from "./TerritoryCardMapOnly";

const templates = [
  {
    id: 'TerritoryCard',
    component: TerritoryCard,
    name: 'Territory Card',
    type: 'territory',
  },
  {
    id: 'TerritoryCardMapOnly',
    component: TerritoryCardMapOnly,
    name: 'Territory Card, map only',
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
    name: 'Region Map',
    type: 'region',
  },
]

const PrintOptionsForm = ({congregationId}) => {
  const availableMapRasters = mapRasters;
  const congregation = getCongregationById(congregationId);
  const availableTerritories = congregation.territories;
  const availableRegions = [
    {id: congregation.id, name: congregation.name},
    ...congregation.subregions
  ];
  return (
    <Formik initialValues={{
      template: templates[0].id,
      language: defaultLanguage,
      mapRaster: availableMapRasters[0].id,
      regions: availableRegions.length > 0 ? [availableRegions[0].id] : [],
      territories: availableTerritories.length > 0 ? [availableTerritories[0].id] : [],
    }}>{({values, setFieldValue}) => {
      const template = templates.find(t => t.id === values.template);
      const mapRasterId = values.mapRaster;
      const mapRaster = availableMapRasters.find(r => r.id === mapRasterId);
      return (
        <>
          <div className="no-print">
            <Form className="pure-form pure-form-stacked">
              <fieldset>
                <legend>Print Options</legend>

                <div className="pure-g">
                  <div className="pure-u-1 pure-u-md-1-2 pure-u-lg-1-3">
                    <label htmlFor="template">Template</label>
                    <Field name="template" id="template" component="select" className="pure-input-1">
                      {templates.map(template =>
                        <option key={template.id} value={template.id}>{template.name}</option>)}
                    </Field>
                  </div>
                </div>

                <div className="pure-g">
                  <div className="pure-u-1 pure-u-md-1-2 pure-u-lg-1-3">
                    <label htmlFor="language">Language</label>
                    <Field name="language" id="language" component="select" className="pure-input-1">
                      {languages.map(({code, name}) =>
                        <option key={code} value={code}>{name}</option>)}
                    </Field>
                  </div>
                </div>

                <div className="pure-g">
                  <div className="pure-u-1 pure-u-md-1-2 pure-u-lg-1-3">
                    <label htmlFor="mapRaster">Map Raster</label>
                    <Field name="mapRaster" id="mapRaster" component="select" className="pure-input-1">
                      {availableMapRasters.map(mapRaster =>
                        <option key={mapRaster.id} value={mapRaster.id}>{mapRaster.name}</option>)}
                    </Field>
                  </div>
                </div>

                {template.type === 'region' &&
                <div className="pure-g">
                  <div className="pure-u-1 pure-u-md-1-2 pure-u-lg-1-3">
                    <label htmlFor="regions">Regions</label>
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
                </div>
                }

                {template.type === 'territory' &&
                <div className="pure-g">
                  <div className="pure-u-1 pure-u-md-1-2 pure-u-lg-1-3">
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
                </div>
                }

              </fieldset>
            </Form>
          </div>

          <IntlProvider locale={values.language} messages={getMessages(values.language)}>
            <>
              {template.type === 'territory' &&
              values.territories.map(territoryId => {
                  return <template.component key={territoryId}
                                             territoryId={territoryId}
                                             congregationId={congregationId}
                                             mapRaster={mapRaster}/>;
                }
              )}
              {template.type === 'region' &&
              values.regions.map(regionId => {
                return <template.component key={regionId}
                                           regionId={regionId}
                                           congregationId={congregationId}
                                           mapRaster={mapRaster}/>;
              })}
            </>
          </IntlProvider>
        </>);
    }}
    </Formik>
  );
};

export default PrintOptionsForm;
