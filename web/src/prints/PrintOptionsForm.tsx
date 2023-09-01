// Copyright © 2015-2023 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import {useEffect} from "react";
import {Field, Form, Formik} from "formik";
import {mapRasters} from "../maps/mapOptions";
import {useCongregationById, useGeneratedQrCodes} from "../api";
import TerritoryCard from "./TerritoryCard";
import NeighborhoodCard from "./NeighborhoodCard";
import RuralTerritoryCard from "./RuralTerritoryCard";
import RegionPrintout from "./RegionPrintout";
import TerritoryCardMapOnly from "./TerritoryCardMapOnly";
import QrCodeOnly from "./QrCodeOnly";
import {usePageState} from "../util";
import {I18nextProvider, useTranslation} from "react-i18next";
import {isolatedI18nInstance, languages} from "../i18n.ts";

const templates = [{
  id: 'TerritoryCard',
  component: TerritoryCard,
  name: 'Territory Card',
  type: 'territory'
}, {
  id: 'TerritoryCardMapOnly',
  component: TerritoryCardMapOnly,
  name: 'Territory Card, map only',
  type: 'territory'
}, {
  id: 'NeighborhoodCard',
  component: NeighborhoodCard,
  name: 'Neighborhood Map',
  type: 'territory'
}, {
  id: 'RuralTerritoryCard',
  component: RuralTerritoryCard,
  name: 'Rural Territory Card',
  type: 'territory'
}, {
  id: 'QrCodeOnly',
  component: QrCodeOnly,
  name: 'QR code only (for adding them as stickers to old cards)',
  type: 'territory'
}, {
  id: 'RegionPrintout',
  component: RegionPrintout,
  name: 'Region Map',
  type: 'region'
}];

interface FormValues {
  template: string;
  language: string;
  mapRaster: string;
  regions: [string];
  territories: [string];
}

function useQrCodeUrls(congregationId, territoryIds) {
  const congregation = useCongregationById(congregationId);
  const {
    data: qrCodes,
    error: qrCodeError
  } = useGeneratedQrCodes(congregationId, territoryIds, !!congregation.permissions.shareTerritoryLink);
  const qrCodeUrls = {};
  (qrCodes || []).forEach(qrCode => {
    qrCodeUrls[qrCode.territory] = qrCode.url
  });
  return {qrCodeUrls, qrCodeError};
}

const Printables = ({template, language, mapRasterId, congregationId, territoryIds, regionIds}) => {
  const {qrCodeUrls, qrCodeError} = useQrCodeUrls(congregationId, territoryIds);
  const i18n = isolatedI18nInstance(language)
  return (
    <>
      {qrCodeError &&
        <div style={{color: "#f00", backgroundColor: "#fee", padding: "1px 1em", border: "1px solid #f00"}}>
          <p>Failed to generate QR codes: {qrCodeError.message}</p>
          <p>Refresh the page and try again.</p>
        </div>
      }
      <I18nextProvider i18n={i18n}>
        <div lang={i18n.language}>
          {template.type === 'territory' && territoryIds.map(territoryId => {
            const qrCodeUrl = qrCodeUrls[territoryId]
            return <template.component key={territoryId}
                                       territoryId={territoryId}
                                       congregationId={congregationId}
                                       qrCodeUrl={qrCodeUrl}
                                       mapRasterId={mapRasterId}/>;
          })}
          {template.type === 'region' && regionIds.map(regionId => {
            return <template.component key={regionId}
                                       regionId={regionId}
                                       congregationId={congregationId}
                                       mapRasterId={mapRasterId}/>;
          })}
        </div>
      </I18nextProvider>
    </>
  )
}

const PrintOptionsForm = ({congregationId}) => {
  const {i18n} = useTranslation();
  const [savedForm, setSavedForm] = usePageState('savedForm', null);
  const congregation = useCongregationById(congregationId);
  const availableMapRasters = mapRasters;
  const availableTerritories = congregation.territories;
  const availableRegions = [{id: congregation.id, name: congregation.name}, ...congregation.regions];
  let initialValues = {
    template: templates[0].id,
    language: i18n.language,
    mapRaster: availableMapRasters[0].id,
    regions: availableRegions.length > 0 ? [availableRegions[0].id] : [],
    territories: availableTerritories.length > 0 ? [availableTerritories[0].id] : []
  } as FormValues;
  if (savedForm) {
    initialValues = savedForm;
  }
  return <Formik
    initialValues={initialValues}
    onSubmit={() => {
    }}>
    {({values, setFieldValue}) => {
      useEffect(() => {
        setSavedForm(values);
      }, [values]);
      const template = templates.find(t => t.id === values.template);
      return <>
        <div className="no-print">
          <Form className="pure-form pure-form-stacked">
            <fieldset>
              <legend>Print Options</legend>

              <div className="pure-g">
                <div className="pure-u-1 pure-u-md-1-2 pure-u-lg-1-3">
                  <label htmlFor="template">Template</label>
                  <Field name="template" id="template" component="select" className="pure-input-1">
                    {templates.map(template => <option key={template.id}
                                                       value={template.id}>{template.name}</option>)}
                  </Field>
                </div>
              </div>

              <div className="pure-g">
                <div className="pure-u-1 pure-u-md-1-2 pure-u-lg-1-3">
                  <label htmlFor="language">Language</label>
                  <Field name="language" id="language" component="select" className="pure-input-1">
                    {languages.map(({code, englishName, nativeName}) =>
                      <option key={code} value={code}>
                        {nativeName}
                        {englishName === nativeName ? '' : ` - ${englishName}`}
                      </option>)}
                  </Field>
                </div>
              </div>

              <div className="pure-g">
                <div className="pure-u-1 pure-u-md-1-2 pure-u-lg-1-3">
                  <label htmlFor="mapRaster">Map Raster</label>
                  <Field name="mapRaster" id="mapRaster" component="select" className="pure-input-1">
                    {availableMapRasters.map(mapRaster => <option key={mapRaster.id}
                                                                  value={mapRaster.id}>{mapRaster.name}</option>)}
                  </Field>
                </div>
              </div>

              {template.type === 'region' && <div className="pure-g">
                <div className="pure-u-1 pure-u-md-1-2 pure-u-lg-1-3">
                  <label htmlFor="regions">Regions</label>
                  <Field name="regions" id="regions" component="select" multiple size={7}
                         className="pure-input-1"
                         onChange={event => setFieldValue("regions", [].slice.call(event.target.selectedOptions).map(option => option.value))}>
                    {availableRegions.map(region => <option key={region.id}
                                                            value={region.id}>{region.name}</option>)}
                  </Field>
                </div>
              </div>}

              {template.type === 'territory' && <div className="pure-g">
                <div className="pure-u-1 pure-u-md-1-2 pure-u-lg-1-3">
                  <label htmlFor="territories">Territories</label>
                  <Field name="territories" id="territories" component="select" multiple size={7}
                         className="pure-input-1"
                         onChange={event => setFieldValue("territories", [].slice.call(event.target.selectedOptions).map(option => option.value))}>
                    {availableTerritories.map(territory => <option key={territory.id} value={territory.id}>
                      {territory.region ? `${territory.number} - ${territory.region}` : territory.number}
                    </option>)}
                  </Field>
                </div>
              </div>}

            </fieldset>
          </Form>
        </div>

        <Printables template={template}
                    language={values.language}
                    mapRasterId={values.mapRaster}
                    congregationId={congregationId}
                    territoryIds={values.territories}
                    regionIds={values.regions}/>
      </>;
    }}
  </Formik>;
};

export default PrintOptionsForm;
