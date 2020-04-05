// Copyright © 2015-2018 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import Lockr from "lockr";

export function savedCongregationId(): string | null | undefined {
  return Lockr.get('congregationId');
}

export function saveCongregationId(congregationId: string) {
  Lockr.set('congregationId', congregationId);
}

export function changeCongregation(congregationId: string) {
  saveCongregationId(congregationId);
  window.location.reload();
}
