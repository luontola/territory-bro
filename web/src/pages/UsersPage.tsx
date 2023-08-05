// Copyright © 2015-2023 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import {useState} from "react";
import {addUser, getCongregationById, getSettings, setUserPermissions} from "../api";
import {ErrorMessage, Field, Form, Formik, FormikErrors} from "formik";
import {sortBy} from "lodash-es";
import styles from "./UsersPage.module.css";
import {formatApiError} from "../errorMessages";
import {Link, useNavigate, useParams} from "react-router-dom";

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

const RemoveUserButton = ({congregation, user, onRemove}) => {
  const navigate = useNavigate();
  const settings = getSettings();
  const myUserId = settings.user.id;
  const isCurrentUser = user.id === myUserId;
  return <button type="button" className={`pure-button ${styles.removeUser}`} onClick={async () => {
    try {
      if (isCurrentUser) {
        if (!confirm(`Are you sure you want to REMOVE YOURSELF from ${congregation.name}? You will not be able to access this congregation anymore.`)) {
          return;
        }
      }
      await setUserPermissions(congregation.id, user.id, [], isCurrentUser);
      if (isCurrentUser) {
        // cannot view this congregation anymore, so redirect to front page
        await navigate('/');
      }
      onRemove?.();
    } catch (error) {
      alert(formatApiError(error));
    }
  }}>
    Remove User
  </button>;
};

function useForceUpdate() {
  const [version, setVersion] = useState(1);
  return () => {
    setVersion(version + 1);
  };
}

const UUID_PATTERN = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";

interface FormValues {
  userId: string;
}

const UsersPage = () => {
  const {congregationId} = useParams()
  const [newUser, setNewUser] = useState(null);
  const forceUpdate = useForceUpdate(); // to trigger re-rendering with the updated user list
  const congregation = getCongregationById(congregationId);
  const settings = getSettings();
  const myUserId = settings.user.id;
  const joinPageUrl = `${location.protocol}//${location.host}/join`;
  // show the new user first for better visibility
  const users = sortBy(congregation.users, user => user.id !== newUser);
  return <Formik
    initialValues={{
      userId: ''
    } as FormValues}
    validate={values => {
      let errors: FormikErrors<FormValues> = {};
      const userId = values.userId.trim();
      if (!userId) {
        errors.userId = 'User ID is required.';
      } else if (!userId.match(`^${UUID_PATTERN}$`)) {
        errors.userId = "That doesn't look like a User ID. It should look something like: 01234567-89ab-cdef-0123-456789abcdef";
      }
      return errors;
    }}
    onSubmit={async (values, {
      setSubmitting,
      resetForm
    }) => {
      try {
        const userId = values.userId.trim();
        await addUser(congregation.id, userId);
        // changing state is needed to trigger re-rendering with the updated user list
        setNewUser(userId);
        resetForm();
      } catch (error) {
        alert(formatApiError(error));
      } finally {
        setSubmitting(false);
      }
    }}>
    {({isSubmitting}) => <>
      <h1>Users</h1>

      <Form className="pure-form pure-form-aligned">
        <fieldset>
          <div className="pure-control-group">
            <label htmlFor="userId">User ID *</label>
            <Field name="userId" id="userId" type="text" autoComplete="off" required={true}
                   pattern={`\\s*${UUID_PATTERN}\\s*`}/>
            <ErrorMessage name="userId" component="span" className="pure-form-message-inline"/>
          </div>

          <p>* To find out somebody's <em>User ID</em>, ask them to visit <Link
            to="/join">{joinPageUrl}</Link> and
            copy their User ID from that page and send it to you.</p>

          <div className="pure-controls">
            <button type="submit" disabled={isSubmitting} className="pure-button pure-button-primary">
              Add User
            </button>
            {' '}
            <Link to=".." relative="path" className="pure-button">Cancel</Link>
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
          <th>Actions</th>
        </tr>
        </thead>
        <tbody>
        {users.map(user => <tr key={user.id} className={user.id === newUser ? styles.newUser : null}>
          <td className={styles.profilePicture}>
            {user.picture && <img src={user.picture} alt=""/>}
          </td>
          <td>{user.name || user.id} {user.id === myUserId && <em>(You)</em>}</td>
          <td>{user.email} {(user.email && !user.emailVerified) && <em>(Unverified)</em>}</td>
          <td><IdentityProvider user={user}/></td>
          <td><RemoveUserButton congregation={congregation} user={user} onRemove={forceUpdate}/></td>
        </tr>)}
        </tbody>
      </table>
    </>}
  </Formik>;
};

export default UsersPage;
