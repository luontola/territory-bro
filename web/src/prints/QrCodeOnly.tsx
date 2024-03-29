// Copyright © 2015-2023 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import {useCongregationById} from "../api";
import styles from "./QrCodeOnly.module.css";
import TerritoryQrCode from "./TerritoryQrCode";
import {memo} from "react";

const QrCodeOnly = ({
                      territory,
                      territoryId,
                      congregation,
                      congregationId,
                      qrCodeUrl,
                    }) => {
  congregation = congregation || useCongregationById(congregationId);
  territory = territory || congregation.getTerritoryById(territoryId);
  return <div className={styles.cropArea}>
    <div className={styles.root}>

      <div className={styles.number}>
        {territory.number}
      </div>

      {qrCodeUrl &&
        <div className={styles.qrCode}>
          <TerritoryQrCode value={qrCodeUrl}/>
        </div>
      }

    </div>
  </div>;
};

export default memo(QrCodeOnly);
