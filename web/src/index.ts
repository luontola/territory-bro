import "./htmx-with-extensions.js";
import './i18n.ts';
import {adjustDropdownWidthToContent} from "./layout/LanguageSelection.ts";
import {getPageState, setPageState} from "./util.ts";
import {installCopyToClipboard} from "./clipboard.ts";
import {NeighborhoodMapElement} from "./maps/NeighborhoodMap.ts";
import {RegionMapElement} from "./maps/RegionMap.ts";
import {TerritoryListMapElement} from "./maps/TerritoryListMap.ts";
import {TerritoryMapElement} from "./maps/TerritoryMap.ts";
import {TerritoryMiniMapElement} from "./maps/TerritoryMiniMap.ts";
import "ol/ol.css";
import "purecss/build/pure.css";
import "purecss/build/grids-responsive.css";
import "@fortawesome/fontawesome-free/css/svg-with-js.css"
import "./styles.ts"; // import last to have higher specificity than the library CSS

function formatXhrError({xhr, pathInfo}) {
  let message = `${xhr.status} ${xhr.statusText} from ${pathInfo?.requestPath}`;
  if (xhr.responseText && xhr.responseText !== xhr.statusText) {
    message += `\n\n${xhr.responseText}`;
  }
  return message;
}

const VALIDATION_ERROR = 422;

// Source: https://stackoverflow.com/questions/69364278/handle-errors-with-htmx
document.body.addEventListener('htmx:beforeOnLoad', function (evt: Event) {
  if (evt instanceof CustomEvent) {
    if (evt.detail.xhr.status === VALIDATION_ERROR) {
      evt.detail.shouldSwap = true;
      evt.detail.isError = false;
    }
  }
});

// Source: https://xvello.net/blog/htmx-error-handling/
document.body.addEventListener('htmx:afterRequest', (event: Event) => {
  const dialog = document.getElementById("htmx-error-dialog") as HTMLDialogElement | null;
  const message = document.getElementById("htmx-error-message");
  if (event instanceof CustomEvent && dialog && message) {
    const status = event.detail.xhr.status
    if (event.detail.successful
      // XXX: when there is a HX-Redirect header, the event.detail.successful field will be undefined; don't report that as an error
      //      See https://github.com/bigskysoftware/htmx/issues/2523
      || (status >= 200 && status < 400)
    ) {
      return;

    } else if (event.detail.failed && event.detail.xhr) {
      console.warn("Server error", event.detail);
      message.innerText = formatXhrError(event.detail);
      dialog.showModal();

    } else {
      const element = event.detail.elt;
      if (element && element.getAttribute('hx-sync')) {
        console.warn("Unspecified error on hx-sync, probably an aborted request", event.detail); // happens when hx-sync aborts a request
        return;
      }
      console.error("Unspecified error", event.detail); // usually network error
      message.innerText = "" + message.getAttribute("data-default-message");
      dialog.showModal();
    }
  }
});

// web components for htmx UI
customElements.define('territory-map', TerritoryMapElement);
customElements.define('territory-list-map', TerritoryListMapElement);
customElements.define('territory-mini-map', TerritoryMiniMapElement);
customElements.define('neighborhood-map', NeighborhoodMapElement);
customElements.define('region-map', RegionMapElement);

// ### territory-bro.ui.layout ###

const languageSelection = document.getElementById("language-selection");
if (languageSelection) {
  const adjust = () => {
    adjustDropdownWidthToContent(languageSelection as any);
    // avoid layout flicker: pass the computed width to the backend for the next page render
    document.cookie = `languageSelectionWidth=${(languageSelection.style.width)}; path=/`
  };
  languageSelection.addEventListener("change", adjust)
  languageSelection.addEventListener("focus", adjust)
  languageSelection.addEventListener("blur", adjust)
  adjust();
}

// ### territory-bro.ui.join-page ####

installCopyToClipboard('#copy-your-user-id');

// ### territory-bro.ui.territory-page ####

installCopyToClipboard('#copy-share-link');

// ### territory-bro.ui.territory-list-page ###

const onTerritorySearch = () => {
  const territorySearch = document.getElementById("territory-search") as HTMLInputElement;
  const clearTerritorySearch = document.getElementById("clear-territory-search")!;
  const territoryList = document.getElementById("territory-list")!;

  setPageState('territorySearch', territorySearch.value);
  const needle = territorySearch.value.toLowerCase().trim();
  clearTerritorySearch.style.display = needle === "" ? "none" : ""

  const visibleTerritories: string[] = [];
  for (const row of territoryList.querySelectorAll("[data-searchable]") as NodeListOf<HTMLElement>) {
    const territoryId = row.getAttribute("data-territory-id");
    const haystack = row.getAttribute("data-searchable");
    if (territoryId && haystack) {
      if (haystack.includes(needle)) {
        visibleTerritories.push(territoryId);
        row.style.display = "";
      } else {
        row.style.display = "none";
      }
    }
  }
  // guard against zero map elements, if it's lazy loaded
  for (const territoryListMap of document.querySelectorAll("territory-list-map")) {
    territoryListMap.setAttribute("visible-territories", JSON.stringify(visibleTerritories));
  }
};
window.onTerritorySearch = onTerritorySearch;

const onClearTerritorySearch = () => {
  const territorySearch = document.getElementById("territory-search") as HTMLInputElement;
  territorySearch.value = "";
  territorySearch.focus();
  onTerritorySearch();
};
window.onClearTerritorySearch = onClearTerritorySearch;

const territorySearch = document.getElementById("territory-search") as HTMLInputElement | null;
if (territorySearch) {
  territorySearch.value = getPageState('territorySearch') ?? '';
  onTerritorySearch(); // apply the search term from page state to filter the rows
}
