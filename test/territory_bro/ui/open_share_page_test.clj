;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.open-share-page-test
  (:require [clojure.test :refer :all]
            [matcher-combinators.test :refer :all]
            [reitit.core :as reitit]
            [territory-bro.domain.share :as share]
            [territory-bro.infra.authentication :as auth]
            [territory-bro.infra.config :as config]
            [territory-bro.infra.middleware :as middleware]
            [territory-bro.test.fixtures :refer :all]
            [territory-bro.test.testutil :as testutil]
            [territory-bro.ui.open-share-page :as open-share-page])
  (:import (clojure.lang ExceptionInfo)
           (java.time Instant)
           (java.util UUID)))

(deftest open-share!-test
  (let [cong-id (UUID. 0 1)
        territory-id (UUID. 0 2)
        share-id (UUID. 0 3)
        share-key "abc123"
        demo-share-key (share/demo-share-key territory-id)
        user-id (UUID. 0 0x10)
        request {:path-params {:share-key share-key}}]
    (testutil/with-events [{:event/type :share.event/share-created
                            :congregation/id cong-id
                            :territory/id territory-id
                            :share/id share-id
                            :share/key share-key
                            :share/type :link}]
      (binding [config/env {:now #(Instant/now)}]

        (testing "open regular share"
          (auth/with-user-id user-id
            (with-fixtures [fake-dispatcher-fixture]
              (let [response (open-share-page/open-share! request)]
                (is (= {:command/type :share.command/record-share-opened
                        :command/user user-id
                        :share/id share-id}
                       (dissoc @*last-command :command/time))
                    "records a history of opening the share")
                (is (= {:status 303
                        :headers {"Location" "/congregation/00000000-0000-0000-0000-000000000001/territories/00000000-0000-0000-0000-000000000002"}
                        :session {:territory-bro.api/opened-shares #{share-id}}
                        ::middleware/mutative-operation? true
                        :body ""}
                       response)
                    "stores in session which shares the user has opened")))))

        (testing "keeps existing session state, supports opening multiple shares"
          (auth/with-anonymous-user
            (with-fixtures [fake-dispatcher-fixture]
              (let [another-share-id (UUID/randomUUID)
                    request (assoc request :session {:territory-bro.api/opened-shares #{another-share-id}
                                                     :other-session-state "stuff"})
                    response (open-share-page/open-share! request)]
                (is (= {:territory-bro.api/opened-shares #{share-id
                                                           another-share-id}
                        :other-session-state "stuff"}
                       (:session response)))))))

        (testing "open demo share"
          (let [request {:path-params {:share-key demo-share-key}}]
            (auth/with-anonymous-user
              (with-fixtures [fake-dispatcher-fixture]
                (let [response (open-share-page/open-share! request)]
                  (is (= {:status 303
                          :headers {"Location" "/congregation/demo/territories/00000000-0000-0000-0000-000000000002"}
                          :body ""}
                         response)
                      "redirects to demo, without touching session state")
                  (is (nil? @*last-command)
                      "does not record that a demo share was opened"))))))

        (testing "share not found"
          (let [request {:path-params {:share-key "bad key"}}]
            (auth/with-anonymous-user
              (with-fixtures [fake-dispatcher-fixture]
                (is (thrown-match? ExceptionInfo
                                   {:type :ring.util.http-response/response
                                    :response {:status 404
                                               :body "Share not found"
                                               :headers {}}}
                                   (open-share-page/open-share! request)))
                (is (nil? @*last-command))))))))))

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
