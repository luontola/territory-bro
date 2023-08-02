// Copyright © 2015-2018 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import * as React from "react";
import styles from "./A4PrintFrame.module.css";

type Props = {
  children?: React.ReactNode;
};

// TODO: parameterize the printout size?

const A4PrintFrame = ({children}: Props) => <div className={styles.cropArea}>
  {children}
</div>;

export default A4PrintFrame;
