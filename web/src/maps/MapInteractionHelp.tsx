// Copyright © 2015-2023 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import InfoBox from "./InfoBox";

const MapInteractionHelp = () => {
  const ctrl = navigator.platform.startsWith('Mac') ? 'Cmd' : 'Ctrl';
  return <InfoBox title={"How to interact with the maps?"}>
    <p><b>Pan:</b> drag with the left mouse button
      / drag with two fingers<br/></p>
    <p><b>Zoom:</b> hold <kbd>{ctrl}</kbd> and scroll with the mouse wheel
      / pinch or spread with two fingers<br/></p>
    <p><b>Rotate:</b> hold <kbd>Alt</kbd>+<kbd>Shift</kbd> and drag with the left mouse button
      / rotate with two fingers</p>
  </InfoBox>
};

export default MapInteractionHelp;
