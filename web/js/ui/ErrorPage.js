// Copyright © 2015-2017 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import React from "react";
import {Layout} from "./Layout";
import type {ErrorMessage} from "../router";

function formatErrorMessage(error) {
  let message = 'Error';
  if (error.status) {
    message += ' ' + error.status;
  }
  message += ': ' + error.message;
  return message;
}

let ErrorPage = ({error}: { error: ErrorMessage }) => (
  <Layout>
    <h1>{formatErrorMessage(error)}</h1>
  </Layout>
);

export {ErrorPage};
