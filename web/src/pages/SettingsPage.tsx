// Copyright © 2015-2023 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import {saveCongregationSettings, useCongregationById} from "../api";
import {ErrorMessage, Field, Form, Formik, FormikErrors} from "formik";
import InfoBox from "../maps/InfoBox";
import style from "./SettingsPage.module.css";
import {Link, useNavigate, useParams} from "react-router-dom";

interface FormValues {
  congregationName: string;
  loansCsvUrl: string;
}

const SettingsPage = ({}) => {
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
        errors.congregationName = 'Congregation name is required.';
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
      <h1>Settings</h1>
      <Form className="pure-form pure-form-aligned">
        <fieldset>
          <div className="pure-control-group">
            <label htmlFor="congregationName">Congregation Name</label>
            <Field name="congregationName" id="congregationName" type="text"/>
            <ErrorMessage name="congregationName" component="span" className="pure-form-message-inline"/>
          </div>

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

            <p>At first, you'll need to create a sheet with the following structure:</p>

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
            <p>The <i>Loaned</i> column should contain "TRUE" when the territory is currently loaned to a publisher and
              "FALSE" when nobody has it.</p>
            <p>The <i>Staleness</i> column should indicate the number of months since the territory was last loaned or
              returned.</p>

            <p>The first row of the sheet must contain the column names, but otherwise the sheet's structure is
              flexible: The columns can be in any order. Columns with other names are ignored. Empty rows are
              ignored.</p>
          </InfoBox>

          <div className="pure-controls">
            <button type="submit" disabled={isSubmitting} className="pure-button pure-button-primary">
              Save
            </button>
            {' '}
            <Link to=".." relative="path" className="pure-button">Cancel</Link>
          </div>
        </fieldset>
      </Form>
    </>}
  </Formik>;
};

export default SettingsPage;
