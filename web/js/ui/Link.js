// Copyright © 2015-2017 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import type {Children} from "react";
import React from "react";
import history from "../history";

function handleClick(event) {
  event.preventDefault();
  history.push({
    pathname: event.currentTarget.pathname,
    search: event.currentTarget.search
  });
}

type Props = { to: string, children?: Children }

const Link = ({to, children, ...props}: Props) => (
  <a href={to} onClick={handleClick} {...props}>{children}</a>
);

export {Link};
