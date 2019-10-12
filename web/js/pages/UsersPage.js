// Copyright © 2015-2019 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import React, {useState} from "react";
import {addUser, getCongregationById} from "../api";
import {Link} from "@reach/router";
import {ErrorMessage, Field, Form, Formik} from "formik";
import sortBy from "lodash/sortBy";
import styles from "./UsersPage.css";

const IdentityProvider = ({user}) => {
  const sub = user.sub || '';
  if (sub.startsWith('google-oauth2|')) {
    return 'Google';
  }
  if (sub.startsWith('facebook|')) {
    return 'Facebook';
  }
  return sub;
};

const UUID_PATTERN = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";

const UsersPage = ({congregationId}) => {
  const [newUser, setNewUser] = useState(null);
  const congregation = getCongregationById(congregationId);
  const joinPageUrl = `${location.protocol}//${location.host}/join`;
  // show the new user first for better visibility
  const users = sortBy(congregation.users, user => user.id !== newUser);
  return (
    <Formik
      initialValues={{
        userId: '',
      }}
      validate={values => {
        let errors = {};
        const userId = values.userId.trim();
        if (!userId) {
          errors.userId = 'User ID is required.';
        } else if (!userId.match(`^${UUID_PATTERN}$`)) {
          errors.userId = 'User ID must to be in UUID format: 01234567-89ab-cdef-0123-456789abcdef';
        }
        return errors;
      }}
      onSubmit={async (values, {setSubmitting, resetForm}) => {
        try {
          const userId = values.userId.trim();
          await addUser(congregation.id, userId);
          // changing state is needed to trigger re-rendering with the updated user list
          setNewUser(userId);
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
              <Field name="userId" id="userId" type="text" autoComplete="off"
                     required={true} pattern={`\\s*${UUID_PATTERN}\\s*`}/>
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
            <th>Login Method</th>
          </tr>
          </thead>
          <tbody>
          {users.map(user => (
            <tr key={user.id}
                className={user.id === newUser ? styles.newUser : null}>
              <td className={styles.profilePicture}>
                {user.picture && <img src={user.picture} alt=""/>}
              </td>
              <td>{user.name || user.id}</td>
              <td>{user.email} {(user.email && !user.emailVerified) && <em>(Unverified)</em>}</td>
              <td><IdentityProvider user={user}/></td>
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
