// Copyright © 2015-2023 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import {getSettings} from "../api";

const HelpPage = () => {
  const {
    supportEmail
  } = getSettings();
  return <>
    <h1>Help</h1>
    <p>Territory Bro is an open source project developed by Esko Luontola.</p>
    <p>The <a href="https://territorybro.com/guide/">User Guide</a> should answer the most common questions.</p>
    {supportEmail && <p>You may also email <a href={`mailto:${supportEmail}`}>{supportEmail}</a>{" "}
      to ask for help with using Territory Bro.</p>}
    <p>Bugs and feature requests may also be reported to{" "}
      <a href="https://github.com/luontola/territory-bro/issues">this project's issue tracker</a>.</p>
  </>;
};

export default HelpPage;
