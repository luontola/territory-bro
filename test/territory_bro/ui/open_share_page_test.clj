(ns territory-bro.ui.open-share-page-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [matcher-combinators.test :refer :all]
            [reitit.core :as reitit]
            [territory-bro.domain.dmz :as dmz]
            [territory-bro.domain.share :as share]
            [territory-bro.infra.authentication :as auth]
            [territory-bro.infra.middleware :as middleware]
            [territory-bro.test.fixtures :refer :all]
            [territory-bro.test.testutil :as testutil]
            [territory-bro.ui.open-share-page :as open-share-page])
  (:import (java.util UUID)))

(deftest open-share!-test
  (let [cong-id (UUID. 0 1)
        territory-id (UUID. 0 2)
        share-id (UUID. 0 3)
        share-key "abc123"
        demo-share-key (share/demo-share-key territory-id)
        request {:path-params {:share-key share-key}}]
    (testutil/with-events [{:event/type :share.event/share-created
                            :congregation/id cong-id
                            :territory/id territory-id
                            :share/id share-id
                            :share/key share-key
                            :share/type :link}]
      (testutil/with-anonymous-user

        (testing "open regular share"
          (with-fixtures [fake-dispatcher-fixture]
            (let [response (open-share-page/open-share! request)]
              (is (= {:command/type :share.command/record-share-opened
                      :command/user auth/anonymous-user-id
                      :share/id share-id}
                     (dissoc @*last-command :command/time))
                  "records a history of opening the share")
              (is (= {:status 303
                      :headers {"Location" "/congregation/00000000-0000-0000-0000-000000000001/territories/00000000-0000-0000-0000-000000000002?share-key=abc123"}
                      :session {::dmz/opened-shares #{share-id}}
                      ::middleware/mutative-operation? true
                      :body ""}
                     response)
                  "stores in session which shares the user has opened"))))

        (testing "keeps existing session state, supports opening multiple shares"
          (with-fixtures [fake-dispatcher-fixture]
            (let [another-share-id (random-uuid)
                  request (assoc request :session {::dmz/opened-shares #{another-share-id}
                                                   :other-session-state "stuff"})
                  response (open-share-page/open-share! request)]
              (is (= {::dmz/opened-shares #{share-id
                                            another-share-id}
                      :other-session-state "stuff"}
                     (:session response))))))

        (testing "if the share has already been opened, does not record an event for it"
          (with-fixtures [fake-dispatcher-fixture]
            (let [session {::dmz/opened-shares #{share-id}}
                  request (assoc request :session session)
                  response (open-share-page/open-share! request)]
              (is (nil? @*last-command)
                  "does not record an event")
              (is (= {:status 303
                      :headers {"Location" "/congregation/00000000-0000-0000-0000-000000000001/territories/00000000-0000-0000-0000-000000000002?share-key=abc123"}
                      :session session
                      ::middleware/mutative-operation? true
                      :body ""}
                     response)
                  "the response is the same as when opening the share the first time"))))

        (testing "open demo share"
          (let [request {:path-params {:share-key demo-share-key}}]
            (with-fixtures [fake-dispatcher-fixture]
              (let [response (open-share-page/open-share! request)]
                (is (= {:status 303
                        :headers {"Location" "/congregation/demo/territories/00000000-0000-0000-0000-000000000002"}
                        :body ""}
                       response)
                    "redirects to demo, without touching session state")
                (is (nil? @*last-command)
                    "does not record that a demo share was opened")))))

        (testing "share not found"
          (let [request {:path-params {:share-key "bad key"}}]
            (with-fixtures [fake-dispatcher-fixture]
              (let [response (open-share-page/open-share! request)]
                (is (= 403 (:status response)))
                (is (str/includes? (:body response) "<h1>Link expired or incorrect"))
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
