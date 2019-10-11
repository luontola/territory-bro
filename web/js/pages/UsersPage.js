// Copyright © 2015-2019 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import React, {useState} from "react";
import {addUser, getCongregationById} from "../api";
import {Link} from "@reach/router";
import {ErrorMessage, Field, Form, Formik} from "formik";

const UsersPage = ({congregationId, navigate}) => {
  const [newUser, setNewUser] = useState(null);
  const congregation = getCongregationById(congregationId);
  const joinPageUrl = `${location.protocol}//${location.host}/join`;
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
      onSubmit={async (values, {setSubmitting, resetForm}) => {
        try {
          await addUser(congregation.id, values.userId);
          // changing state is needed to trigger re-rendering with the updated user list
          setNewUser(values.userId);
          resetForm();
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

        <p>To add users to this congregation, ask them to visit <Link to="/join">{joinPageUrl}</Link> and tell you
          their <em>user ID</em> from that page. You can then input their user ID to the following form.</p>

        <Form className="pure-form pure-form-aligned">
          <fieldset>
            <div className="pure-control-group">
              <label htmlFor="userId">User ID</label>
              <Field name="userId" id="userId" type="text" autoComplete="off"/>
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

        <table className="pure-table pure-table-horizontal">
          <thead>
          <tr>
            <th/>
            <th>Name</th>
            <th>Email</th>
          </tr>
          </thead>
          <tbody>
          {congregation.users.map(user => (
            <tr key={user.id}
                style={{backgroundColor: user.id === newUser ? '#ffc' : ''}}>
              <td style={{textAlign: 'center', padding: 0}}>
                {user.picture &&
                <img src={user.picture}
                     alt=""
                     style={{
                       height: '3em',
                       width: '3em',
                       display: 'block', // needed to avoid a mysterious 4px margin below the image
                     }}/>
                }
              </td>
              <td>{user.name || user.id}</td>
              <td>{user.email} {(user.email && !user.emailVerified) && <i>(Unverified)</i>}</td>
            </tr>
          ))}
          </tbody>
        </table>
      </>
    )}
    </Formik>
  );
};

export default UsersPage;
