// Copyright © 2015-2023 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import {useEffect, useState} from "react";
import {openShare} from "../api";
import {useNavigate, useParams} from "react-router-dom";

const OpenSharePage = () => {
  const {shareKey} = useParams()
  const navigate = useNavigate();
  const [error, setError] = useState(null);

  useEffect(() => {
    (async () => {
      try {
        const share = await openShare(shareKey);
        navigate(`/congregation/${share.congregation}/territories/${share.territory}`,
          {replace: true});
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
    return <p>Please wait...</p>;
  }
};

export default OpenSharePage;
