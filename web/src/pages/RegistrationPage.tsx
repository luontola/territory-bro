// Copyright © 2015-2023 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import {ErrorMessage, Field, Form, Formik, FormikErrors} from "formik";
import {createCongregation, useSettings} from "../api";
import {auth0Authenticator} from "../authentication";
import {useNavigate} from "react-router-dom";
import {Trans, useTranslation} from "react-i18next";

interface FormValues {
  congregationName: string;
}

const RegistrationPage = () => {
  const {t} = useTranslation();
  const navigate = useNavigate();
  const settings = useSettings();
  if (!settings.user) {
    auth0Authenticator(settings).login();
    return <p>Please wait, you will be redirected...</p>;
  }

  return <>
    <h1>{t('RegistrationPage.title')}</h1>

    <Formik
      initialValues={{congregationName: ""} as FormValues}
      validate={values => {
        let errors: FormikErrors<FormValues> = {};
        if (!values.congregationName) {
          errors.congregationName = t('SettingsPage.congregationNameRequired');
        }
        return errors;
      }}
      onSubmit={async (values, {setSubmitting}) => {
        try {
          const id = await createCongregation(values.congregationName);
          navigate(`/congregation/${id}`);
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
            <label htmlFor="congregationName">{t('SettingsPage.congregationName')}</label>
            <Field type="text" name="congregationName" id="congregationName" autoComplete="off"/>
            <ErrorMessage name="congregationName" component="div" className="pure-form-message-inline"/>
          </div>
          <div className="pure-controls">
            <button type="submit" disabled={isSubmitting} className="pure-button pure-button-primary">
              {t('RegistrationPage.register')}
            </button>
          </div>
        </fieldset>
      </Form>}
    </Formik>

    <p><Trans i18nKey="RegistrationPage.mailingListAd">
      <a href="https://groups.google.com/forum/#!forum/territory-bro-announcements/join"></a>
    </Trans></p>
  </>;
};

export default RegistrationPage;
