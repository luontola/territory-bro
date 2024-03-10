;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.open-share-page-test
  (:require [clojure.test :refer :all]
            [reitit.core :as reitit]
            [territory-bro.ui.open-share-page :as open-share-page]))

(deftest routes-test
  (let [router (reitit/router open-share-page/routes)]
    (doseq [path ["/share/9S7S8l_bfSc/123"
                  "/share/9S7S8l_bfSc/"
                  "/share/9S7S8l_bfSc"
                  "/share/demo-r4_xpNQySdO4xR7iOhPKKQ/123"]]
      (testing (str "accepts share link format: " path)
        (is (= open-share-page/open-share!
               (-> (reitit/match-by-path router path)
                   :data :get :handler)))))))
