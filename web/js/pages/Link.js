// Copyright © 2015-2018 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import * as React from 'react';
import history from "../history";

function handleClick(event: MouseEvent) {
  event.preventDefault();
  const target = ((event.currentTarget: any): HTMLAnchorElement);
  history.push({
    pathname: target.pathname,
    search: target.search
  });
}

const Link = ({href, children, ...props}: {
  href: string,
  children?: React.Node
}) => (
  <a href={href} onClick={handleClick} {...props}>{children}</a>
);

export default Link;
