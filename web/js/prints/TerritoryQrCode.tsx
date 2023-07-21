// Copyright © 2015-2023 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import React from "react";
import {QRCode} from "react-qr-svg";

const TerritoryQrCode = ({value}) => {
  return <QRCode fgColor="#000000"
                 bgColor="#FFFFFF"
                 level="M"
                 style={{width: "100%"}}
                 value={value}/>
}

export default TerritoryQrCode;
