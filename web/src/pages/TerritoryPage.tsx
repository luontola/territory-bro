// Copyright © 2015-2023 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import {useState} from "react";
import {shareTerritory, useCongregationById} from "../api";
import styles from "./TerritoryPage.module.css"
import TerritoryMap from "../maps/TerritoryMap";
import {mapRasters} from "../maps/mapOptions";
import MapInteractionHelp from "../maps/MapInteractionHelp";
import ClipboardJS from "clipboard";
import {useParams} from "react-router-dom";
import {faCopy, faShareNodes, faXmark} from "@fortawesome/free-solid-svg-icons";
import {FontAwesomeIcon} from "@fortawesome/react-fontawesome";
import {useTranslation} from "react-i18next";
import PageTitle from "../layout/PageTitle.tsx";

const mapRaster = mapRasters[0];

new ClipboardJS('#copy-share-link');

const ShareButton = ({congregationId, territoryId, territoryNumber}) => {
  const {t} = useTranslation();
  const [open, setOpen] = useState(false);
  const [shareButton, setShareButton] = useState<HTMLButtonElement | null>(null);
  const [shareUrl, setShareUrl] = useState<string | null>(null);

  const togglePopup = async () => {
    if (!shareUrl) {
      const url = await shareTerritory(congregationId, territoryId);
      // TODO: change "#/" to a "/" after nobody has a cached page older than 2023-07-31, to avoid incompatibility with the old router
      setShareUrl(url + '#/' + encodeURIComponent(territoryNumber).replaceAll(/%../g, "_"));
    }
    setOpen(!open);
  }
  const closePopup = () => {
    shareButton?.focus();
    setOpen(false);
  }

  return (
    <form className="pure-form" onSubmit={event => event.preventDefault()}>
      <button type="button"
              className={`pure-button${open ? ' pure-button-active' : ''}`}
              aria-expanded={open ? 'true' : 'false'}
              onClick={togglePopup}
              ref={setShareButton}>
        <FontAwesomeIcon icon={faShareNodes}/> {t('TerritoryPage.shareLink.button')}
      </button>

      {open &&
        <div className={styles.sharePopup}>
          <button type="button"
                  className={`${styles.closeButton} pure-button`}
                  onClick={closePopup}>
            <FontAwesomeIcon icon={faXmark} title={t('TerritoryPage.shareLink.closePopup')}/>
          </button>

          <label htmlFor="share-link">
            {t('TerritoryPage.shareLink.description')}
          </label>

          <div className={styles.shareLink}>
            <input type="text"
                   id="share-link"
                   value={shareUrl}
              // effectively read-only, but allow selection
              // with keyboard and don't show it grayed out
                   onChange={() => null}
                   aria-readonly="true"/>

            <button type="button"
                    id="copy-share-link"
                    className="pure-button"
                    data-clipboard-target="#share-link">
              <FontAwesomeIcon icon={faCopy} title={t('TerritoryPage.shareLink.copy')}/>
            </button>
          </div>
        </div>
      }
    </form>
  );
}

const TerritoryPage = () => {
  const {t, i18n} = useTranslation();
  const {congregationId, territoryId} = useParams()
  const congregation = useCongregationById(congregationId);
  const territory = congregation.getTerritoryById(territoryId);
  // TODO: consider using a grid layout for responsiveness so that the details area has fixed width
  return <>
    <PageTitle title={t('TerritoryPage.title', {number: territory.number})}/>

    <div className="pure-g">
      <div className="pure-u-1 pure-u-sm-2-3 pure-u-md-1-2 pure-u-lg-1-3 pure-u-xl-1-4">
        <div className={styles.details}>
          <table className="pure-table pure-table-horizontal">
            <tbody>
            <tr>
              <th>{t('Territory.number')}</th>
              <td>{territory.number}</td>
            </tr>
            <tr>
              <th>{t('Territory.region')}</th>
              <td>{territory.region}</td>
            </tr>
            <tr>
              <th>{t('Territory.addresses')}</th>
              <td>{territory.addresses}</td>
            </tr>
            </tbody>
          </table>
        </div>

        {congregation.permissions.shareTerritoryLink &&
          <div className={styles.actions}>
            <ShareButton congregationId={congregationId}
                         territoryId={territoryId}
                         territoryNumber={territory.number}/>
          </div>
        }
      </div>

      <div className="pure-u-1 pure-u-lg-2-3 pure-u-xl-3-4">
        <div className={styles.map}>
          <TerritoryMap territory={territory} mapRaster={mapRaster} printout={false} key={i18n.resolvedLanguage}/>
        </div>
        <div className="no-print">
          <MapInteractionHelp/>
        </div>
      </div>
    </div>
  </>;
};

export default TerritoryPage;
