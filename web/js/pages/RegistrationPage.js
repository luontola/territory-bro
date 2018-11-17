// Copyright © 2015-2018 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import React from "react";
import Layout from "../layout/Layout";
import type {State} from "../reducers";
import {connect} from "react-redux";

type Props = {
  userId: string,
  supportEmail: string,
};

let RegistrationPage = ({userId, supportEmail}: Props) => (
  <Layout>
    <h1>Registration</h1>

    <p>Your user ID is <span style={{fontSize: "120%", fontWeight: "bold"}}>{userId}</span></p>

    <p>To add a new congregation to Territory Bro, send email
      to <a href={`mailto:${supportEmail}?subject=Territory Bro Registration`}>{supportEmail}</a> and mention:</p>
    <ul>
      <li>your <em>user ID</em> (shown above)</li>
      <li>the <em>name and city</em> of your congregation</li>
      <li>the <em>language</em> of your congregation, unless Territory Bro already supports it
        (see the "Change language" list at the top of this page)
      </li>
    </ul>

    <p>It is recommended to <a href="https://groups.google.com/forum/#!forum/territory-bro-announcements/join">subscribe
      to the announcements mailing list</a> to be notified about important updates to Territory Bro.</p>
  </Layout>
);

function mapStateToProps(state: State): Props {
  return {
    userId: state.api.userId || '',
    supportEmail: state.api.supportEmail,
  };
}

RegistrationPage = connect(mapStateToProps)(RegistrationPage);

export default RegistrationPage;
