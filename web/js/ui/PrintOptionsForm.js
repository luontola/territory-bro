// Copyright © 2015-2017 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import React from "react";
import {Field, formValueSelector, reduxForm} from "redux-form";
import type {MapRaster} from "../maps";
import {defaultMapRaster, mapRasters} from "../maps";
import {connect} from "react-redux";

const formName = 'printOptions';
const selector = formValueSelector(formName);

let PrintOptionsForm = ({handleSubmit}) => (
  <form onSubmit={handleSubmit}>
    <p><b>Map Raster </b>
      <Field name="mapRaster" component="select">
        {mapRasters.map(map => <option value={map.id} key={map.id}>{map.name}</option>)}
      </Field></p>
  </form>
);
PrintOptionsForm = reduxForm({form: formName})(PrintOptionsForm);
PrintOptionsForm = connect(mapStateToProps)(PrintOptionsForm);
export default PrintOptionsForm;

function mapStateToProps() {
  return {
    initialValues: {
      mapRaster: defaultMapRaster.id,
    }
  }
}

export function getMapRaster(state: {}): MapRaster {
  const id = selector(state, 'mapRaster');
  return mapRasters.find(map => map.id === id) || defaultMapRaster;
}
