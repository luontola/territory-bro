// Copyright © 2015-2023 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import {ReactNode} from "react";
import styles from "./A5PrintFrame.module.css";

type Props = {
  children?: ReactNode;
};

// TODO: parameterize the printout size?

const A5PrintFrame = ({children}: Props) => <div className={styles.cropArea}>
  {children}
</div>;

export default A5PrintFrame;
