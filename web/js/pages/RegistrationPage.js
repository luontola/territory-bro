// Copyright © 2015-2019 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import React from "react";
import {ErrorMessage, Field, Form, Formik} from "formik";
import {createCongregation, getSettings} from "../api";
import {buildAuthenticator} from "../authentication";
import {navigate} from "@reach/router";

const RegistrationPage = () => {
  const settings = getSettings();
  const loggedIn = settings.user.authenticated;
  const userId = settings.user.id;

  if (!loggedIn) {
    const {domain, clientId} = settings.auth0;
    const auth = buildAuthenticator(domain, clientId);
    auth.login();
    return <p>Please wait, you will be redirected...</p>;
  }

  return (
    <>
      <h1>Registration</h1>

      <h2>Join an Existing Congregation</h2>

      <p>Ask the brother who is taking care of the territories in your congregation to give you Territory Bro
        access.</p>

      <p>You will need to tell him your User ID, which is: <div
        style={{fontSize: '150%', margin: '15px'}}>{userId}</div></p>

      <h2>Register a New Congregation</h2>

      <Formik initialValues={{congregationName: ""}}
              validate={values => {
                let errors = {};
                if (!values.congregationName) {
                  errors.congregationName = "Congregation name is required.";
                }
                return errors;
              }}
              onSubmit={async (values, {setSubmitting}) => {
                try {
                  const id = await createCongregation(values.congregationName);
                  // TODO: use short IDs
                  navigate(`/congregation/${id}`);
                } catch (e) {
                  console.error('Form submit failed:', e);
                  alert(e);
                } finally {
                  setSubmitting(false)
                }
              }}>
        {({isSubmitting}) => (
          <Form className="pure-form pure-form-aligned">
            <fieldset>
              <div className="pure-control-group">
                <label htmlFor="congregationName">Congregation Name</label>
                <Field type="text" name="congregationName" id="congregationName"/>
                <ErrorMessage name="congregationName" component="div" className="pure-form-message-inline"/>
              </div>
              <div className="pure-controls">
                <button type="submit" disabled={isSubmitting} className="pure-button pure-button-primary">
                  Register
                </button>
              </div>
            </fieldset>
          </Form>
        )}
      </Formik>

      <p>It is recommended to <a href="https://groups.google.com/forum/#!forum/territory-bro-announcements/join">subscribe
        to the announcements mailing list</a> to be notified about important updates to Territory Bro.</p>
    </>
  );
};

export default RegistrationPage;
