// Copyright © 2015-2023 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import React from "react";
import styles from "./PrintDateNotice.module.css";
import formatDate from "date-fns/format";

type Props = {
  children?: React.ReactNode;
};

const PrintDateNotice = ({children}: Props) => {
  const today = formatDate(new Date(), 'yyyy-MM-dd');
  return <div className={styles.root}>
    <div className={styles.notice}>
      Printed {today} with TerritoryBro.com
    </div>
    <div className={styles.content}>
      {children}
    </div>
  </div>;
};

export default PrintDateNotice;
