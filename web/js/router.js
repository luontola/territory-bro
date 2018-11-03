// Copyright © 2015-2018 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import toRegex from "path-to-regexp";

export type ErrorMessage = {
  message: string,
  status?: number,
}
export type RoutingContext = {
  pathname: string,
  error?: ErrorMessage,
  [string]: string,
};
export type Route = {
  path: string,
  action: Function,
}

export function matchURI(path: string, uri: string): ?{ [string]: string } {
  const keys = [];
  const pattern: RegExp = toRegex(path, keys); // TODO: Use caching
  const match: ?string[] = pattern.exec(uri);
  if (!match) {
    return null;
  }
  const params: { [string]: string } = /* :: {} || */ Object.create(null);
  for (let i = 1; i < match.length; i++) {
    params[keys[i - 1].name] = (match[i] !== undefined ? match[i] : undefined);
  }
  return params;
}

async function resolve(routes: Array<Route>, context: RoutingContext): Promise<mixed> {
  for (const route of routes) {
    const uri = context.error ? '/error' : context.pathname;
    const params = matchURI(route.path, uri);
    if (!params) {
      continue;
    }
    const result = await route.action({...context, params});
    if (result) {
      return result;
    }
  }
  const error: ErrorMessage = new Error('Page Not Found');
  error.status = 404;
  throw error;
}

export default {resolve};
