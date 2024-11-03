import ClipboardJS from "clipboard";

export function installCopyToClipboard(target: string) {
  const clipboard = new ClipboardJS(target);
  clipboard.on('success', (event) => {
    (window as any).latestCopyToClipboard = event.text; // expose for tests
  });
}
