// Copyright © 2015-2019 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import React from "react";
import {addUser, getCongregationById} from "../api";
import {Link} from "@reach/router";
import {ErrorMessage, Field, Form, Formik} from "formik";

const UsersPage = ({congregationId, navigate}) => {
  const congregation = getCongregationById(congregationId);
  return (
    <Formik
      initialValues={{
        userId: '',
      }}
      validate={values => {
        let errors = {};
        if (!values.userId) {
          errors.userId = 'User ID is required.';
        }
        return errors;
      }}
      onSubmit={async (values, {setSubmitting}) => {
        try {
          await addUser(congregation.id, values.userId);
          navigate('..');
        } catch (e) {
          console.error('Form submit failed:', e);
          alert(e);
        } finally {
          setSubmitting(false);
        }
      }}
    >{({isSubmitting}) => (
      <>
        <h1><Link to="..">{congregation.name}</Link>: Users</h1>
        <Form className="pure-form pure-form-aligned">
          <fieldset>
            <div className="pure-control-group">
              <label htmlFor="userId">User ID</label>
              <Field name="userId" id="userId" type="text"/>
              <ErrorMessage name="userId" component="span" className="pure-form-message-inline"/>
            </div>

            <div className="pure-controls">
              <button type="submit"
                      disabled={isSubmitting}
                      className="pure-button pure-button-primary">
                Add User
              </button>
              {' '}
              <Link to=".." className="pure-button">Cancel</Link>
            </div>
          </fieldset>
        </Form>
      </>
    )}
    </Formik>
  );
};

export default UsersPage;
