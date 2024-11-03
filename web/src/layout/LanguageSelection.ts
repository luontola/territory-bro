const canvas = document.createElement("canvas");
const context = canvas.getContext("2d");

function getTextWidth(text, font) {
  if (!context) {
    return 100;
  }
  context.font = font;
  const metrics = context.measureText(text);
  return metrics.width;
}

function getCssProperty(element, prop) {
  return window.getComputedStyle(element, null).getPropertyValue(prop);
}

function getElementFont(element) {
  const fontWeight = getCssProperty(element, 'font-weight') || 'normal';
  const fontSize = getCssProperty(element, 'font-size') || '16px';
  const fontFamily = getCssProperty(element, 'font-family') || 'Times New Roman';
  return `${fontWeight} ${fontSize} ${fontFamily}`;
}

export function adjustDropdownWidthToContent(element?: HTMLSelectElement) {
  if (!element) {
    return;
  }
  const text = element.selectedOptions[0]?.innerText;
  if (!text) {
    return;
  }
  const font = getElementFont(element);
  const hasFocus = element === document.activeElement;
  const dropdownAppearanceAndPaddingWidth = 42; // how much wider the element is on Chrome when it has focus
  const width = Math.ceil(getTextWidth(text, font)) + (hasFocus ? dropdownAppearanceAndPaddingWidth : 0);
  element.style.width = width + "px";
}
