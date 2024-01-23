// Copyright © 2015-2024 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import TerritoryMap from "../maps/TerritoryMap";
import TerritoryMiniMap from "../maps/TerritoryMiniMap";
import {useCongregationById, useTerritoryById} from "../api";
import styles from "./Finland2024TerritoryCard.module.css";
import PrintDateNotice from "./PrintDateNotice";
import TerritoryQrCode from "./TerritoryQrCode";
import {findMapRasterById} from "../maps/mapOptions.ts";
import {memo} from "react";
import {useTranslation} from "react-i18next";
import A4PrintFrame from "./A4PrintFrame.tsx";
import logo from "./finland-2024-logo.png"

const Finland2024TerritoryCard = ({
                                    territory,
                                    territoryId,
                                    congregation,
                                    congregationId,
                                    qrCodeUrl,
                                    mapRaster,
                                    mapRasterId
                                  }) => {
  const {t} = useTranslation();
  congregation = congregation || useCongregationById(congregationId);
  territory = territory || congregation.getTerritoryById(territoryId);
  territory = useTerritoryById(congregation.id, territory.id);
  mapRaster = mapRaster || findMapRasterById(mapRasterId);
  return <A4PrintFrame>
    <link rel="preconnect" href="https://fonts.googleapis.com"/>
    <link rel="preconnect" href="https://fonts.gstatic.com" crossOrigin="anonymous"/>
    <link href="https://fonts.googleapis.com/css2?family=Montserrat:wght@400;700&display=swap" rel="stylesheet"/>
    <div className={styles.superRoot}>

      <div className={styles.root}>
        <div className={styles.minimap}>
          <TerritoryMiniMap territory={territory} congregation={congregation} printout={true}/>
        </div>

        <img className={styles.logo} src={logo} alt=""></img>

        <div className={styles.header}>
          <div className={styles.title}>
            {t('TerritoryCard.title')}
          </div>
          <div className={styles.region}>
            {territory.region}
          </div>
        </div>

        <div className={styles.number}>
          {territory.number}
        </div>

        <div className={styles.map}>
          <PrintDateNotice>
            <TerritoryMap territory={territory} mapRaster={mapRaster} printout={true}/>
          </PrintDateNotice>
        </div>

        {qrCodeUrl &&
          <div className={styles.qrCode}>
            <TerritoryQrCode value={qrCodeUrl}/>
          </div>
        }
      </div>

      <div>
        {territory.doNotCalls &&
          <h3 style={{color: "red"}}>Älä käy: {territory.doNotCalls}</h3>
        }
        <h3>Palauta käytynä palautuslaatikkoon</h3>
        <p style={{marginBottom: "5cm"}}>Huomioita:</p>
      </div>

    </div>
  </A4PrintFrame>;
};

export default memo(Finland2024TerritoryCard);
