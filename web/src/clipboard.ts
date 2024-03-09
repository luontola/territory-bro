// Copyright © 2015-2024 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import ClipboardJS from "clipboard";

export function installCopyToClipboard(target: string) {
  const clipboard = new ClipboardJS(target);
  clipboard.on('success', (event) => {
    (window as any).latestCopyToClipboard = event.text;
  });
}
