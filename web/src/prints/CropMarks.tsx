// Copyright © 2015-2018 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import * as React from "react";
import styles from "./CropMarks.module.css";

type Props = {
  children?: React.ReactNode;
};

// TODO: use webpack for the images, preferably minimize and embed them
// TODO: parameterize the printout size?

const CropMarks = ({children}: Props) => {
  return <div className={styles.root}>
    <div className={styles.topLeft}><img src="/img/crop-mark.svg" alt=""/></div>
    <div className={styles.topRight}><img src="/img/crop-mark.svg" alt=""/></div>
    <div className={styles.cropArea}>{children}</div>
    <div className={styles.bottomLeft}><img src="/img/crop-mark.svg" alt=""/></div>
    <div className={styles.bottomRight}><img src="/img/crop-mark.svg" alt=""/></div>
  </div>;
};

export default CropMarks;
