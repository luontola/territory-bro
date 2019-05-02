// Copyright © 2015-2019 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import React from "react";
import Layout from "../layout/Layout";
import {useCongregations} from "../api";

const CongregationPage = ({congregationId}) => {
  const congregation = useCongregations().find(cong => cong.id === congregationId);
  return (
    <Layout>
      <h1>{congregation.name}</h1>
    </Layout>
  );
};

export default CongregationPage;
