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
