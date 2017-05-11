// Copyright © 2015-2017 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import combineReducers from "redux/es/combineReducers";
import type {ApiState} from "./apiReducer";
import api from "./apiReducer";
import {reducer as form} from "redux-form";

export type State = {
  api: ApiState,
  form: any
}

export default combineReducers({
  api,
  form,
});
