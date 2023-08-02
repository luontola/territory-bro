// Copyright © 2015-2020 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import React from "react";
import styles from "./InfoBox.module.css"

const InfoBox = ({title, children}) => {
  return <div className={styles.root}>
    {title &&
    <div className={styles.title}>
      <i className="fas fa-info-circle"/> {title}
    </div>
    }
    <div className={styles.content}>
      {children}
    </div>
  </div>
};

export default InfoBox;
