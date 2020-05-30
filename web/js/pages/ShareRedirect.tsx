// Copyright © 2015-2020 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import React, {useEffect, useState} from "react";
import {openShare} from "../api";
import {navigate} from "@reach/router";

const ShareRedirect = ({shareKey}) => {
  const [error, setError] = useState(null);

  useEffect(() => {
    (async () => {
      try {
        const share = await openShare(shareKey);
        navigate(`/congregation/${share.congregation}/territories/${share.territory}`);
      } catch (e) {
        setError(e);
      }
    })();
  }, []);

  if (error) {
    return <>
      <h1>Link not found</h1>
      <p>The link you opened may be incorrect or it has expired.</p>
    </>;
  } else {
    return <h1>Please wait...</h1>;
  }
};

export default ShareRedirect;
