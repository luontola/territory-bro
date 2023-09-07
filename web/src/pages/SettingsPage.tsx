// Copyright © 2015-2023 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import {saveCongregationSettings, useCongregationById} from "../api";
import {ErrorMessage, Field, Form, Formik, FormikErrors} from "formik";
import InfoBox from "../maps/InfoBox";
import style from "./SettingsPage.module.css";
import {Link, useNavigate, useParams} from "react-router-dom";
import {useTranslation} from "react-i18next";

interface FormValues {
  congregationName: string;
  loansCsvUrl: string;
}

const SettingsPage = ({}) => {
  const {t} = useTranslation();
  const {congregationId} = useParams()
  const navigate = useNavigate();
  const congregation = useCongregationById(congregationId);
  return <Formik
    initialValues={{
      congregationName: congregation.name,
      loansCsvUrl: congregation.loansCsvUrl || '',
    } as FormValues}
    validate={values => {
      let errors: FormikErrors<FormValues> = {};
      if (!values.congregationName) {
        errors.congregationName = t('SettingsPage.congregationNameRequired');
      }
      if (values.loansCsvUrl && !values.loansCsvUrl.startsWith("https://docs.google.com/")) {
        errors.loansCsvUrl = 'If present, must point to a Google Sheets share';
      }
      return errors;
    }}
    onSubmit={async (values, {setSubmitting}) => {
      try {
        await saveCongregationSettings(congregation.id, values.congregationName, values.loansCsvUrl);
        navigate('..', {relative: "path"});
      } catch (e) {
        console.error('Form submit failed:', e);
        alert(e);
      } finally {
        setSubmitting(false);
      }
    }}>
    {({isSubmitting}) => <>
      <h1>{t('SettingsPage.title')}</h1>
      <Form className="pure-form pure-form-aligned">
        <fieldset>
          <div className="pure-control-group">
            <label htmlFor="congregationName">{t('SettingsPage.congregationName')}</label>
            <Field name="congregationName" id="congregationName" type="text"/>
            <ErrorMessage name="congregationName" component="span" className="pure-form-message-inline"/>
          </div>

          <h3>{t('SettingsPage.experimentalFeatures')}</h3>

          <div lang="en">
            <div className="pure-control-group">
              <label htmlFor="loansCsvUrl">Territory loans CSV URL (optional)</label>
              <Field name="loansCsvUrl" id="loansCsvUrl" type="text" size="50"/>
              <ErrorMessage name="loansCsvUrl" component="span" className="pure-form-message-inline"/>
            </div>

            <InfoBox title={"Early Access Feature: Integrate with territory loans data from Google Sheets"}>
              <p>If you keep track of your territory loans using Google Sheets, it's possible to export the data from
                there and visualize it on the map on Territory Bro's Territories page. Eventually Territory Bro will
                handle the territory loans accounting all by itself, but in the meanwhile this workaround gives some of
                the benefits.</p>

              <p>Here is an <a
                href="https://docs.google.com/spreadsheets/d/1pa_EIyuCpWGbEOXFOqjc7P0XfDWbZNRKIKXKLpnKkx4/edit?usp=sharing">
                example spreadsheet</a> that you can use as a starting point. Also please <Link to="/support">contact
                me</Link> for assistance and so that I will know to help you later with migration to full accounting
                support.
              </p>

              <p>You'll need to create a sheet with the following structure:</p>

              <table className={style.spreadsheet}>
                <tbody>
                <tr>
                  <td>Number</td>
                  <td>Loaned</td>
                  <td>Staleness</td>
                </tr>
                <tr>
                  <td>101</td>
                  <td>TRUE</td>
                  <td>2</td>
                </tr>
                <tr>
                  <td>102</td>
                  <td>FALSE</td>
                  <td>6</td>
                </tr>
                </tbody>
              </table>

              <p>The <i>Number</i> column should contain the territory number. It's should match the territories in
                Territory Bro.</p>
              <p>The <i>Loaned</i> column should contain "TRUE" when the territory is currently loaned to a publisher
                and "FALSE" when nobody has it.</p>
              <p>The <i>Staleness</i> column should indicate the number of months since the territory was last loaned or
                returned.</p>

              <p>The first row of the sheet must contain the column names, but otherwise the sheet's structure is
                flexible: The columns can be in any order. Columns with other names are ignored. Empty rows are
                ignored.</p>

              <p>After you have such a sheet, you can expose it to the Internet through <tt>File | Share | Publish to
                web</tt>. Publish that sheet as a CSV file and enter its URL to the above field on this settings page.
              </p>
            </InfoBox>
          </div>

          <div className="pure-controls">
            <button type="submit" disabled={isSubmitting} className="pure-button pure-button-primary">
              {t('SettingsPage.save')}
            </button>
            {' '}
            <Link to=".." relative="path" className="pure-button">
              {t('SettingsPage.cancel')}
            </Link>
          </div>
        </fieldset>
      </Form>
    </>}
  </Formik>;
};

export default SettingsPage;
