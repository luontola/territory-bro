// Copyright © 2015-2019 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import * as React from 'react';
import history from "../history";

const LEFT_MOUSE_BUTTON = 0;

function openInCurrentTab(event: MouseEvent) {
  const link = ((event.currentTarget: any): HTMLAnchorElement);
  return (!link.target || link.target === "_self");
}

function modifierKeysPressed(event: MouseEvent) {
  return !!(event.ctrlKey || event.altKey || event.shiftKey || event.metaKey);
}

function handleClick(event: MouseEvent) {
  if (event.button === LEFT_MOUSE_BUTTON && openInCurrentTab(event) && !modifierKeysPressed(event)) {
    event.preventDefault();
    const link = ((event.currentTarget: any): HTMLAnchorElement);
    history.push({
      pathname: link.pathname,
      search: link.search
    });
  }
}

const Link = ({href, children, ...props}: {
  href: string,
  children?: React.Node
}) => (
  <a href={href} onClick={handleClick} {...props}>{children}</a>
);

export default Link;
