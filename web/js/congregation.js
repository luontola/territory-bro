// Copyright © 2015-2018 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import Lockr from "lockr";

export function savedCongregationId(): ?string {
  return Lockr.get('congregationId');
}

export function changeCongregation(congregationId: string) {
  Lockr.set('congregationId', congregationId);
  window.location.reload();
}
