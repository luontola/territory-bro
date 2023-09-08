// Copyright © 2015-2023 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import {useState} from "react";
import {addUser, setUserPermissions, useCongregationById, useSettings} from "../api";
import {ErrorMessage, Field, Form, Formik, FormikErrors} from "formik";
import {sortBy} from "lodash-es";
import styles from "./UserManagement.module.css";
import {formatApiError} from "../errorMessages";
import {Link, useNavigate, useParams} from "react-router-dom";
import {Trans, useTranslation} from "react-i18next";

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

const RemoveUserButton = ({congregation, user}) => {
  const {t} = useTranslation();
  const navigate = useNavigate();
  const settings = useSettings();
  const myUserId = settings.user.id;
  const isCurrentUser = user.id === myUserId;
  return <button type="button" className={`pure-button ${styles.removeUser}`} onClick={async () => {
    try {
      if (isCurrentUser) {
        if (!confirm(t('UserManagement.removeYourselfWarning', {congregation: congregation.name}))) {
          return;
        }
      }
      await setUserPermissions(congregation.id, user.id, [], isCurrentUser);
      if (isCurrentUser) {
        // cannot view this congregation anymore, so redirect to front page
        navigate('/');
      }
    } catch (error) {
      alert(formatApiError(error));
    }
  }}>
    {t('UserManagement.removeUser')}
  </button>;
};

const UUID_PATTERN = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";

interface FormValues {
  userId: string;
}

const UserManagement = () => {
  const {t} = useTranslation();
  const {congregationId} = useParams()
  const [newUser, setNewUser] = useState<string | null>(null);
  const congregation = useCongregationById(congregationId);
  const settings = useSettings();
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
        errors.userId = t('UserManagement.userIdRequired');
      } else if (!userId.match(`^${UUID_PATTERN}$`)) {
        errors.userId = t('UserManagement.userIdWrongFormat');
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
      <h2>{t('UserManagement.title')}</h2>

      <Form className="pure-form pure-form-aligned">
        <fieldset>
          <div className="pure-control-group">
            <label htmlFor="userId">{t('UserManagement.userId')} *</label>
            <Field name="userId" id="userId" type="text" autoComplete="off" required={true}
                   pattern={`\\s*${UUID_PATTERN}\\s*`}/>
            <ErrorMessage name="userId" component="span" className="pure-form-message-inline"/>
          </div>

          <p>* <Trans i18nKey="UserManagement.userIdHint" values={{joinPageUrl}}>
            <Link to="/join"></Link>
          </Trans></p>

          <div className="pure-controls">
            <button type="submit" disabled={isSubmitting} className="pure-button pure-button-primary">
              {t('UserManagement.addUser')}
            </button>
          </div>
        </fieldset>
      </Form>

      <table className="pure-table pure-table-horizontal">
        <thead>
        <tr>
          <th/>
          <th>{t('UserManagement.name')}</th>
          <th>{t('UserManagement.email')}</th>
          <th>{t('UserManagement.loginMethod')}</th>
          <th>{t('UserManagement.actions')}</th>
        </tr>
        </thead>
        <tbody>
        {users.map(user => <tr key={user.id} className={user.id === newUser ? styles.newUser : null}>
          <td className={styles.profilePicture}>
            {user.picture && <img src={user.picture} alt=""/>}
          </td>
          <td>{user.name || user.id} {user.id === myUserId && <em>({t('UserManagement.you')})</em>}</td>
          <td>{user.email} {(user.email && !user.emailVerified) && <em>({t('UserManagement.unverified')})</em>}</td>
          <td><IdentityProvider user={user}/></td>
          <td><RemoveUserButton congregation={congregation} user={user}/></td>
        </tr>)}
        </tbody>
      </table>
    </>}
  </Formik>;
};

export default UserManagement;
