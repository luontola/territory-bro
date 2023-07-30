// Copyright © 2015-2023 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import React, {useState} from "react";
import {getCongregationById, shareTerritory} from "../api";
import styles from "./TerritoryPage.css"
import TerritoryMap from "../maps/TerritoryMap";
import {mapRasters} from "../maps/mapOptions";
import MapInteractionHelp from "../maps/MapInteractionHelp";
import ClipboardJS from "clipboard";

const mapRaster = mapRasters[0];

new ClipboardJS('#copy-share-link');

const ShareButton = ({congregationId, territoryId, territoryNumber}) => {
  const [open, setOpen] = useState(false);
  const [shareButton, setShareButton] = useState(null);
  const [shareUrl, setShareUrl] = useState(null);

  const togglePopup = async () => {
    if (!shareUrl) {
      const url = await shareTerritory(congregationId, territoryId);
      setShareUrl(url + '?n=' + encodeURIComponent(territoryNumber).replaceAll(/%../g, "_"));
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
        <i className="fas fa-share-alt"/> Share a link
      </button>

      {open &&
        <div className={styles.sharePopup}>
          <button type="button"
                  className={`${styles.closeButton} pure-button`}
                  onClick={closePopup}>
            <i className="fas fa-times" title="Close"/>
          </button>

          <label htmlFor="share-link">
            People with this link will be able to view this territory map without logging in:
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
              <i className="fas fa-copy" title="Copy to clipboard"/>
            </button>
          </div>
        </div>
      }
    </form>
  );
}

const TerritoryPage = ({congregationId, territoryId}) => {
  const congregation = getCongregationById(congregationId);
  const territory = congregation.getTerritoryById(territoryId);
  // TODO: consider using a grid layout for responsiveness so that the details area has fixed width
  return <>
    <h1>Territory {territory.number}</h1>

    <div className="pure-g">
      <div className="pure-u-1 pure-u-sm-2-3 pure-u-md-1-2 pure-u-lg-1-3 pure-u-xl-1-4">
        <div className={styles.details}>
          <table className="pure-table pure-table-horizontal">
            <tbody>
            <tr>
              <th>Number</th>
              <td>{territory.number}</td>
            </tr>
            <tr>
              <th>Region</th>
              <td>{territory.region}</td>
            </tr>
            <tr>
              <th>Addresses</th>
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
          <TerritoryMap territory={territory} mapRaster={mapRaster} printout={false}/>
        </div>
        <div className="no-print">
          <MapInteractionHelp/>
        </div>
      </div>
    </div>
  </>;
};

export default TerritoryPage;
