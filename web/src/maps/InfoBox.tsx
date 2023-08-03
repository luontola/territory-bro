// Copyright © 2015-2023 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import {FontAwesomeIcon} from '@fortawesome/react-fontawesome';
import {faInfoCircle} from '@fortawesome/free-solid-svg-icons';
import styles from "./InfoBox.module.css"

const InfoBox = ({title, children}) => {
  return <div className={styles.root}>
    {title &&
      <div className={styles.title}>
        <FontAwesomeIcon icon={faInfoCircle}/> {title}
      </div>
    }
    <div className={styles.content}>
      {children}
    </div>
  </div>
};

export default InfoBox;
