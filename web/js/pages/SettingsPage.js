// Copyright © 2015-2019 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import React from "react";
import {getCongregationById, renameCongregation} from "../api";
import {Link} from "@reach/router";
import {Field, Form, Formik} from "formik";

const SettingsPage = ({congregationId, navigate}) => {
  const congregation = getCongregationById(congregationId);
  return (
    <Formik
      initialValues={{
        name: congregation.name,
      }}
      onSubmit={async (values, {setSubmitting}) => {
        try {
          // TODO: show success/failure message
          // TODO: validate name
          await renameCongregation(congregationId, values.name);
          navigate('..');
        } finally {
          setSubmitting(false);
        }
      }}
    >{({isSubmitting}) => (
      <>
        <h1><Link to="..">{congregation.name}</Link>: Settings</h1>
        <Form className="pure-form pure-form-aligned">
          <fieldset>
            <div className="pure-control-group">
              <label htmlFor="name">Congregation Name</label>
              <Field name="name" id="name" type="text"/>
            </div>

            <div className="pure-controls">
              <button type="submit"
                      disabled={isSubmitting}
                      className="pure-button pure-button-primary">
                Save
              </button>
            </div>
          </fieldset>
        </Form>
      </>
    )}
    </Formik>
  );
};

export default SettingsPage;
