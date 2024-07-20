// Copyright © 2015-2024 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

export function parseFontAwesomeIcon(svg: string) {
  const icon = new DOMParser().parseFromString(svg, "image/svg+xml").firstChild as SVGElement;
  icon.setAttribute("class", "svg-inline--fa")
  icon.setAttribute("role", "img")
  icon.setAttribute("aria-hidden", "true")
  icon.setAttribute("focusable", "false")
  icon.querySelectorAll("path").forEach(path => {
    path.setAttribute("fill", "currentColor");
  })
  return icon;
}
