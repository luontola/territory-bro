// Copyright © 2015-2017 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import React from "react";
import styles from "./PrintDateNotice.css";
import formatDate from "date-fns/format";

const PrintDateNotice = ({children}: {
  children?: React.Element<*>,
}) => {
  const today = formatDate(new Date(), 'YYYY-MM-DD');
  return (
    <div className={styles.root}>
      <div className={styles.notice}>
        Printed {today} with TerritoryBro.com
      </div>
      <div className={styles.content}>
        {children}
      </div>
    </div>
  );
};

export default PrintDateNotice;
