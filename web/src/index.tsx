// Copyright © 2015-2023 Esko Luontola
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

function NavigationListener({children}) {
  const location = useLocation()
  useEffect(() => {
    console.info(`Current URL is now ${location.pathname}${location.search}${location.hash}`);
    logPageView();
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
    document.querySelector('main')?.scrollIntoView({block: "start"});
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

createRoot(document.getElementById('root')!)
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
