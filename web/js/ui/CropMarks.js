// Copyright © 2015-2017 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import "../../css/territory-cards.css";
import React from "react";

// TODO: use local CSS
// TODO: use webpack for the images, preferably minimize and embed them
// TODO: parameterize the printout size?

const CropMarks = ({children, className}: {
  children?: React.Element<*>,
  className: string,
}) => {
  return (
    <div className="croppable-territory-card">
      <div className="crop-mark-top-left"><img src="/img/crop-mark.svg" alt=""/></div>
      <div className="crop-mark-top-right"><img src="/img/crop-mark.svg" alt=""/></div>
      <div className={"crop-area " + className}>{children}</div>
      <div className="crop-mark-bottom-left"><img src="/img/crop-mark.svg" alt=""/></div>
      <div className="crop-mark-bottom-right"><img src="/img/crop-mark.svg" alt=""/></div>
    </div>
  );
};

export default CropMarks;
