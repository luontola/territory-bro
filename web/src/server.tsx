// Copyright © 2015-2024 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import i18n from "./i18n.ts";
import Layout from "./layout/Layout.tsx";
import TerritoryPage from "./pages/TerritoryPage.tsx";
import React from "react";

export function myFun(param: any): string {
  return i18n.t('TerritoryPage.title', {number: param})
}

export function renderTerritoryPage(json: string) {
  const data = JSON.parse(json);
  return (
    <Layout>
      <TerritoryPage/>
    </Layout>
  );
}

console.log("server.tsx loaded");
