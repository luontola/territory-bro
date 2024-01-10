// Copyright © 2015-2024 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

export function myFun(param: any): string {
  return `Hello ${param} from server.tsx`;
}

console.log("server.tsx loaded");
