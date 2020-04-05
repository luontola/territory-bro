// Copyright © 2015-2020 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import React from "react";
import {ErrorMessage, Field, Form, Formik, FormikErrors} from "formik";
import {createCongregation, getSettings} from "../api";
import {buildAuthenticator} from "../authentication";
import {navigate} from "@reach/router";

interface FormValues {
  congregationName: string;
}

const RegistrationPage = () => {
  const settings = getSettings();
  if (!settings.user) {
    const {
      domain,
      clientId
    } = settings.auth0;
    const auth = buildAuthenticator(domain, clientId);
    auth.login();
    return <p>Please wait, you will be redirected...</p>;
  }

  return <>
    <h1>Register a New Congregation</h1>

    <Formik
      initialValues={{congregationName: ""} as FormValues}
      validate={values => {
        let errors: FormikErrors<FormValues> = {};
        if (!values.congregationName) {
          errors.congregationName = "Congregation name is required.";
        }
        return errors;
      }}
      onSubmit={async (values, {setSubmitting}) => {
        try {
          const id = await createCongregation(values.congregationName);
          // TODO: use short IDs
          await navigate(`/congregation/${id}`);
        } catch (e) {
          console.error('Form submit failed:', e);
          alert(e);
        } finally {
          // TODO: react-dom.development.js:88 Warning: Can't perform a React state update on an unmounted component. This is a no-op, but it indicates a memory leak in your application. To fix, cancel all subscriptions and asynchronous tasks in a useEffect cleanup function.
          setSubmitting(false);
        }
      }}>
      {({isSubmitting}) => <Form className="pure-form pure-form-aligned">
        <fieldset>
          <div className="pure-control-group">
            <label htmlFor="congregationName">Congregation Name</label>
            <Field type="text" name="congregationName" id="congregationName" autoComplete="off"/>
            <ErrorMessage name="congregationName" component="div" className="pure-form-message-inline"/>
          </div>
          <div className="pure-controls">
            <button type="submit" disabled={isSubmitting} className="pure-button pure-button-primary">
              Register
            </button>
          </div>
        </fieldset>
      </Form>}
    </Formik>

    <p>It is recommended to <a href="https://groups.google.com/forum/#!forum/territory-bro-announcements/join">subscribe
      to the announcements mailing list</a> to be notified about important updates to Territory Bro.</p>
  </>;
};

export default RegistrationPage;
