// Copyright © 2015-2023 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import {QRCodeSVG} from "qrcode.react";

const TerritoryQrCode = ({value}) => {
  return <QRCodeSVG value={value}
                    level="M"
                    style={{width: "100%", height: "auto"}}/>
}

export default TerritoryQrCode;
