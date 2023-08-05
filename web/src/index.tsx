// Copyright © 2015-2023 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import React, {useEffect} from "react";
import {createRoot} from "react-dom/client";
import {IntlProvider} from "react-intl";
import {language, messages} from "./intl";
import ErrorBoundary from "react-error-boundary";
import HomePage from "./pages/HomePage";
import CongregationPage from "./pages/CongregationPage";
import RegistrationPage from "./pages/RegistrationPage";
import LoginCallbackPage from "./pages/LoginCallbackPage";
import NotFoundPage from "./pages/NotFoundPage";
import Layout from "./layout/Layout";
import PrintoutPage from "./pages/PrintoutPage";
import ErrorPage from "./pages/ErrorPage";
import HelpPage from "./pages/HelpPage";
import {logPageView} from "./analytics";
import SettingsPage from "./pages/SettingsPage";
import UsersPage from "./pages/UsersPage";
import JoinPage from "./pages/JoinPage";
import TerritoryListPage from "./pages/TerritoryListPage";
import TerritoryPage from "./pages/TerritoryPage";
import OpenSharePage from "./pages/OpenSharePage";
import {getPageState, setPageState} from "./util";
import {BrowserRouter, Route, Routes, useLocation} from "react-router-dom";
import {QueryClientProvider} from "@tanstack/react-query";
import {queryClient} from "./api.ts";

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

document.querySelector('html')
  .setAttribute('lang', language);

createRoot(document.getElementById('root'))
  .render(
    <React.StrictMode>
      <ErrorBoundary FallbackComponent={ErrorPage}>
        <IntlProvider locale={language} messages={messages}>
          <BrowserRouter>
            <NavigationListener>
              <QueryClientProvider client={queryClient}>
                <React.Suspense fallback={<p>Loading....</p>}>
                  <Layout>
                    <Routes>
                      <Route path="/" element={<HomePage/>}/>
                      <Route path="/join" element={<JoinPage/>}/>
                      <Route path="/login-callback" element={<LoginCallbackPage/>}/>
                      <Route path="/register" element={<RegistrationPage/>}/>
                      <Route path="/help" element={<HelpPage/>}/>
                      <Route path="/share/:shareKey/*" element={<OpenSharePage/>}/>

                      <Route path="/congregation/:congregationId" element={<CongregationPage/>}/>
                      <Route path="/congregation/:congregationId/territories" element={<TerritoryListPage/>}/>
                      <Route path="/congregation/:congregationId/territories/:territoryId" element={<TerritoryPage/>}/>
                      <Route path="/congregation/:congregationId/printouts" element={<PrintoutPage/>}/>
                      <Route path="/congregation/:congregationId/settings" element={<SettingsPage/>}/>
                      <Route path="/congregation/:congregationId/users" element={<UsersPage/>}/>

                      <Route path="*" element={<NotFoundPage/>}/>
                    </Routes>
                  </Layout>
                </React.Suspense>
              </QueryClientProvider>
            </NavigationListener>
          </BrowserRouter>
        </IntlProvider>
      </ErrorBoundary>
    </React.StrictMode>
  );
