// Copyright © 2015-2023 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import React from "react";
import {ErrorBoundary} from "react-error-boundary";
import HomePage from "./pages/HomePage";
import CongregationPage from "./pages/CongregationPage";
import RegistrationPage from "./pages/RegistrationPage";
import LoginCallbackPage from "./pages/LoginCallbackPage";
import NotFoundPage from "./pages/NotFoundPage";
import Layout from "./layout/Layout";
import PrintoutPage from "./pages/PrintoutPage";
import ErrorPage from "./pages/ErrorPage";
import SupportPage from "./pages/SupportPage.tsx";
import SettingsPage from "./pages/SettingsPage";
import JoinPage from "./pages/JoinPage";
import TerritoryListPage from "./pages/TerritoryListPage";
import TerritoryPage from "./pages/TerritoryPage";
import OpenSharePage from "./pages/OpenSharePage";
import {Route, Routes} from "react-router-dom";
import LoadingPage from "./pages/LoadingPage.tsx";

const App = () => (
  <ErrorBoundary FallbackComponent={ErrorPage}>
    <React.Suspense fallback={<LoadingPage/>}>
      <Layout>
        <Routes>
          <Route path="/" element={<HomePage/>}/>
          <Route path="/join" element={<JoinPage/>}/>
          <Route path="/login-callback" element={<LoginCallbackPage/>}/>
          <Route path="/register" element={<RegistrationPage/>}/>
          <Route path="/support" element={<SupportPage/>}/>
          <Route path="/share/:shareKey/*" element={<OpenSharePage/>}/>

          <Route path="/congregation/:congregationId" element={<CongregationPage/>}/>
          <Route path="/congregation/:congregationId/territories" element={<TerritoryListPage/>}/>
          <Route path="/congregation/:congregationId/territories/:territoryId" element={<TerritoryPage/>}/>
          <Route path="/congregation/:congregationId/printouts" element={<PrintoutPage/>}/>
          <Route path="/congregation/:congregationId/settings" element={<SettingsPage/>}/>
          <Route path="/congregation/:congregationId/support" element={<SupportPage/>}/>

          <Route path="*" element={<NotFoundPage/>}/>
        </Routes>
      </Layout>
    </React.Suspense>
  </ErrorBoundary>
);

export default App;
