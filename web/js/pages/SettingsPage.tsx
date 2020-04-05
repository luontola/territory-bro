// Copyright © 2015-2019 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import React from "react";
import {getCongregationById, renameCongregation} from "../api";
import {Link} from "@reach/router";
import {ErrorMessage, Field, Form, Formik} from "formik";

const SettingsPage = ({
  congregationId,
  navigate
}) => {
  const congregation = getCongregationById(congregationId);
  return <Formik initialValues={{
    congregationName: congregation.name
  }} validate={values => {
    let errors = {};
    if (!values.congregationName) {
      errors.congregationName = 'Congregation name is required.';
    }
    return errors;
  }} onSubmit={async (values, {
    setSubmitting
  }) => {
    try {
      await renameCongregation(congregation.id, values.congregationName);
      navigate('..');
    } catch (e) {
      console.error('Form submit failed:', e);
      alert(e);
    } finally {
      setSubmitting(false);
    }
  }}>{({
      isSubmitting
    }) => <>
        <h1><Link to="..">{congregation.name}</Link>: Settings</h1>
        <Form className="pure-form pure-form-aligned">
          <fieldset>
            <div className="pure-control-group">
              <label htmlFor="congregationName">Congregation Name</label>
              <Field name="congregationName" id="congregationName" type="text" />
              <ErrorMessage name="congregationName" component="span" className="pure-form-message-inline" />
            </div>

            <div className="pure-controls">
              <button type="submit" disabled={isSubmitting} className="pure-button pure-button-primary">
                Save
              </button>
              {' '}
              <Link to=".." className="pure-button">Cancel</Link>
            </div>
          </fieldset>
        </Form>
      </>}
    </Formik>;
};

export default SettingsPage;