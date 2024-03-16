// Copyright © 2015-2024 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import React, {useEffect} from "react";
import {createRoot} from "react-dom/client";
import {logPageView} from "./analytics";
import {getPageState, setPageState} from "./util";
import {BrowserRouter, useLocation} from "react-router-dom";
import {QueryClientProvider} from "@tanstack/react-query";
import {queryClient} from "./api.ts";
import App from "./App.tsx";
import './i18n.ts';
import htmx from 'htmx.org';
import {TerritoryMapElement} from "./maps/TerritoryMap.ts";
import {adjustDropdownWidthToContent} from "./layout/LanguageSelection.tsx";

window.htmx = htmx;

function NavigationListener({children}) {
  const location = useLocation()
  useEffect(() => {
    console.info(`Current URL is now ${location.pathname}${location.search}${location.hash}`);
    logPageView();
    // TODO: should also restore the focused element - or just switch to server-side rendering for things to work out of the box
    // TODO: investigate React Router's ScrollRestoration component https://reactrouter.com/en/main/components/scroll-restoration
    restoreScrollY(getPageState("scrollY"));
  }, [location.pathname])
  return children;
}

function restoreScrollY(scrollY?: number) {
  if (typeof scrollY === 'number') {
    // XXX: scrolling immediately does not scroll far enough; maybe we must wait for React to render the whole page
    setTimeout(() => {
      window.scrollTo(0, scrollY);
    }, 100);
  } else {
    window.scrollTo(0, 0);
  }
}

function listenScrollY(onScroll) {
  let lastKnownScrollY = 0;
  let ticking = false;
  window.addEventListener('scroll', _event => {
    lastKnownScrollY = window.scrollY;
    if (!ticking) {
      // avoiding firing too many events, as advised in https://developer.mozilla.org/en-US/docs/Web/API/Element/scroll_event
      setTimeout(() => {
        onScroll(lastKnownScrollY);
        ticking = false;
      }, 200);
      ticking = true;
    }
  });
}

listenScrollY(scrollY => {
  setPageState('scrollY', scrollY);
})

const root = document.getElementById('root');
if (root) {
  createRoot(root)
    .render(
      <React.StrictMode>
        <BrowserRouter>
          <NavigationListener>
            <QueryClientProvider client={queryClient}>
              <App/>
            </QueryClientProvider>
          </NavigationListener>
        </BrowserRouter>
      </React.StrictMode>
    );
}

function formatXhrError({status, statusText, responseText}) {
  let message = `${status} ${statusText}`;
  if (responseText && responseText !== statusText) {
    message += ` - ${responseText}`;
  }
  return message;
}

// Source: https://xvello.net/blog/htmx-error-handling/
document.body.addEventListener('htmx:afterRequest', (event: Event) => {
  const dialog = document.getElementById("htmx-error-dialog") as HTMLDialogElement | null;
  const message = document.getElementById("htmx-error-message");
  if (event instanceof CustomEvent && dialog && message) {
    if (event.detail.successful) {
      dialog.close();
      message.innerText = "";

    } else if (event.detail.failed && event.detail.xhr) {
      console.warn("Server error", event.detail);
      message.innerText = formatXhrError(event.detail.xhr);
      dialog.showModal();

    } else {
      console.error("Unspecified error", event.detail); // usually network error
      message.innerText = "" + message.getAttribute("data-default-message");
      dialog.showModal();
    }
  }
});

// web components for htmx UI
customElements.define('territory-map', TerritoryMapElement);

// event handlers for htmx UI
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
