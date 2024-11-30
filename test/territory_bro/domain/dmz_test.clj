(ns territory-bro.domain.dmz-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [matcher-combinators.matchers :as match]
            [matcher-combinators.test :refer :all]
            [ring.mock.request :as mock]
            [ring.util.response :as response]
            [territory-bro.dispatcher :as dispatcher]
            [territory-bro.domain.congregation :as congregation]
            [territory-bro.domain.demo :as demo]
            [territory-bro.domain.dmz :as dmz]
            [territory-bro.domain.do-not-calls :as do-not-calls]
            [territory-bro.domain.do-not-calls-test :as do-not-calls-test]
            [territory-bro.domain.loan :as loan]
            [territory-bro.domain.publisher :as publisher]
            [territory-bro.domain.share :as share]
            [territory-bro.domain.testdata :as testdata]
            [territory-bro.infra.authentication :as auth]
            [territory-bro.infra.config :as config]
            [territory-bro.infra.permissions :as permissions]
            [territory-bro.test.fixtures :refer :all]
            [territory-bro.test.testutil :as testutil :refer [replace-in]])
  (:import (clojure.lang ExceptionInfo)
           (java.time LocalDate LocalTime OffsetDateTime ZoneOffset)
           (java.util UUID)))

(def cong-id (UUID. 0 1))
(def user-id (UUID. 0 2))
(def user-id2 (UUID. 0 3))
(def territory-id (UUID. 0 4))
(def territory-id2 (UUID. 0 5))
(def congregation-boundary-id (UUID. 0 6))
(def region-id (UUID. 0 7))
(def card-minimap-viewport-id (UUID. 0 8))
(def assignment-id (UUID. 0 9))
(def publisher-id (UUID. 0 10))
(def share-id (UUID. 0 11))
(def share-key "abc123")
(def demo-share-key "demo-AAAAAAAAAAAAAAAAAAAABA")
(def ^LocalDate start-date (LocalDate/of 2000 1 1))
(def ^LocalDate covered-date (LocalDate/of 2000 2 1))
(def ^LocalDate end-date (LocalDate/of 2000 3 1))
(def ^LocalDate today (LocalDate/of 2000 4 1))
(def test-time (.toInstant (OffsetDateTime/of today LocalTime/NOON ZoneOffset/UTC)))

(def test-publisher
  {:congregation/id cong-id
   :publisher/id publisher-id
   :publisher/name "John Doe"})
(def test-publishers-by-id {cong-id {publisher-id test-publisher}})

(def congregation-created
  {:event/type :congregation.event/congregation-created
   :congregation/id cong-id
   :congregation/name "Cong1 Name"
   :congregation/schema-name "cong1_schema"})

(def settings-updated
  {:event/type :congregation.event/settings-updated
   :congregation/id cong-id
   :congregation/loans-csv-url "https://docs.google.com/spreadsheets/123"})

(def gis-user-created
  {:event/type :congregation.event/gis-user-created
   :congregation/id cong-id
   :user/id user-id
   :gis-user/username "username123"
   :gis-user/password "password123"})

(def territory-defined
  {:event/type :territory.event/territory-defined
   :congregation/id cong-id
   :territory/id territory-id
   :territory/number "123"
   :territory/addresses "the addresses"
   :territory/region "the region"
   :territory/meta {:foo "bar"}
   :territory/location testdata/wkt-helsinki-rautatientori})
(def territory-defined2
  (assoc territory-defined
         :territory/id territory-id2
         :territory/number "456"
         :territory/location testdata/wkt-helsinki-kauppatori))

(def congregation-boundary-defined
  {:event/type :congregation-boundary.event/congregation-boundary-defined
   :congregation/id cong-id
   :congregation-boundary/id congregation-boundary-id
   :congregation-boundary/location testdata/wkt-helsinki})

(def region-defined
  {:event/type :region.event/region-defined
   :congregation/id cong-id
   :region/id region-id
   :region/name "the name"
   :region/location testdata/wkt-south-helsinki})

(def card-minimap-viewport-defined
  {:event/type :card-minimap-viewport.event/card-minimap-viewport-defined
   :congregation/id cong-id
   :card-minimap-viewport/id card-minimap-viewport-id
   :card-minimap-viewport/location testdata/wkt-polygon})

(def territory-assigned
  {:event/type :territory.event/territory-assigned
   :congregation/id cong-id
   :territory/id territory-id
   :assignment/id assignment-id
   :assignment/start-date start-date
   :publisher/id publisher-id})
(def territory-covered
  {:event/type :territory.event/territory-covered
   :congregation/id cong-id
   :territory/id territory-id
   :assignment/id assignment-id
   :assignment/covered-date covered-date})
(def territory-returned ; TODO: not needed? it reduces the amount of state (no current assignment)
  {:event/type :territory.event/territory-returned
   :congregation/id cong-id
   :territory/id territory-id
   :assignment/id assignment-id
   :assignment/end-date end-date})

(def share-created
  {:event/type :share.event/share-created
   :share/id share-id
   :share/key share-key
   :share/type :link
   :congregation/id cong-id
   :territory/id territory-id})
(def share-opened
  {:event/type :share.event/share-opened
   :share/id share-id})

(def test-events
  (->> (flatten [congregation-created
                 settings-updated
                 (congregation/admin-permissions-granted cong-id user-id)
                 (congregation/admin-permissions-granted cong-id user-id2)
                 gis-user-created
                 territory-defined
                 territory-defined2
                 congregation-boundary-defined
                 region-defined
                 card-minimap-viewport-defined
                 territory-assigned
                 territory-covered
                 share-created])
       (mapv #(assoc % :event/time test-time))))

(def demo-events
  (concat [demo/congregation-created]
          (into [] demo/transform-gis-events test-events)
          (demo/generate-assignment-events territory-id today)))

;; deterministically generated random assignments (update manually if the generator algorithm is changed)
(def demo-current-assignment
  {:assignment/id #uuid "47c9933c-737b-dfc2-3ae8-be2f9642e6d3"
   :assignment/start-date (LocalDate/of 2000 3 29)
   :publisher/id #uuid "4b475264-5e76-f39e-81a8-e923dd4d7273"
   :publisher/name "Matthias"})
(def demo-last-covered (LocalDate/of 1999 11 23))

(defn- apply-share-opened [state]
  (share/grant-opened-shares state [share-id] (auth/current-user-id)))

(def not-logged-in
  {:type :ring.util.http-response/response
   :response {:status 401
              :body "Not logged in"
              :headers {}}})
(def access-denied
  {:type :ring.util.http-response/response
   :response {:status 403
              :body "Access denied"
              :headers {}}})

(def env
  {:public-url "https://example.com"
   :qr-code-base-url "https://qr.example.com"
   :gis-database-host "gis.example.com"
   :gis-database-name "gis-db"})

(def testdata-fixture
  (join-fixtures [(fixed-clock-fixture test-time)
                  (fn [f]
                    (binding [config/env env
                              publisher/publishers-by-id (fn [_conn cong-id]
                                                           (get test-publishers-by-id cong-id))
                              do-not-calls/get-do-not-calls do-not-calls-test/fake-get-do-not-calls]
                      (testutil/with-events (concat test-events demo-events)
                        (f))))]))

(use-fixtures :once testdata-fixture)


;;;; Commands

(deftest wrap-demo-session-test
  (testutil/with-user-id user-id
    (binding [dispatcher/command! (fn [_conn _state _command]
                                    (assert false "should not be called"))]
      (let [assignment-id (-> (dmz/get-territory "demo" territory-id)
                              :territory/current-assignment
                              :assignment/id)
            return-command {:command/type :territory.command/return-territory
                            :congregation/id "demo"
                            :territory/id territory-id
                            :assignment/id assignment-id
                            :date today
                            :returning? true
                            :covered? false}
            returned-event {:assignment/end-date today
                            :assignment/id assignment-id
                            :congregation/id "demo"
                            :event/type :territory.event/territory-returned
                            :territory/id territory-id}
            request (mock/request :get "/foo")]

        (testing "saves demo events into session"
          (let [handler (-> (fn handler [_request]
                              (dmz/dispatch! return-command)
                              (response/response "dummy"))
                            dmz/wrap-demo-session)]
            (is (= {:status 200
                    :headers {}
                    :body "dummy"
                    :session {:demo {:events [returned-event]
                                     :do-not-calls nil}}}
                   (handler request)))))

        (testing "appends demo events after previous events in the session"
          (let [handler (-> (fn handler [_request]
                              (dmz/dispatch! return-command)
                              (response/response "dummy"))
                            dmz/wrap-demo-session)]
            (is (= {:status 200
                    :headers {}
                    :body "dummy"
                    :session {:demo {:events [{:event/type :previous-dummy-event}
                                              returned-event]
                                     :do-not-calls nil}}}
                   (handler (assoc request :session {:demo {:events [{:event/type :previous-dummy-event}]}}))))))

        (testing "demo events from session are applied to state"
          (let [current-assignment #(:territory/current-assignment (dmz/get-territory "demo" territory-id))
                handler (-> (fn handler [_request]
                              (is (nil? (current-assignment)))
                              (response/response "dummy"))
                            dmz/wrap-demo-session)]
            (is (some? (current-assignment)))
            (is (= {:status 200
                    :headers {}
                    :body "dummy"}
                   (handler (assoc request :session {:demo {:events [returned-event]}}))))))

        (testing "handlers can add unrelated stuff to the session"
          (let [handler (-> (fn handler [_request]
                              (dmz/dispatch! return-command)
                              (-> (response/response "dummy")
                                  (assoc :session {:unrelated "stuff"})))
                            dmz/wrap-demo-session)]
            (is (= {:status 200
                    :headers {}
                    :body "dummy"
                    :session {:unrelated "stuff"
                              :demo {:events [returned-event]
                                     :do-not-calls nil}}}
                   (handler request)))))

        (testing "doesn't modify the session if no new events were dispatched"
          (let [handler (-> (fn handler [_request]
                              (response/response "dummy"))
                            dmz/wrap-demo-session)]
            (is (= {:status 200
                    :headers {}
                    :body "dummy"}
                   (handler (assoc request :session {:dummy "stuff"}))))))

        (testing "saves demo do-not-calls into session"
          (let [handler (-> (fn handler [_request]
                              (dmz/dispatch! {:command/type :do-not-calls.command/save-do-not-calls
                                              :congregation/id "demo"
                                              :territory/id territory-id
                                              :territory/do-not-calls "the do-not-calls"})
                              (response/response "dummy"))
                            dmz/wrap-demo-session)]
            (is (= {:status 200
                    :headers {}
                    :body "dummy"
                    :session {:demo {:do-not-calls {territory-id "the do-not-calls"}
                                     :events nil}}}
                   (handler request)))))

        (testing "reads demo do-not-calls from session"
          (let [handler (-> (fn handler [_request]
                              (response/response (dmz/get-do-not-calls "demo" territory-id)))
                            dmz/wrap-demo-session)]
            (is (= {:status 200
                    :headers {}
                    :body "the do-not-calls"}
                   (handler (assoc request :session {:demo {:do-not-calls {territory-id "the do-not-calls"}}}))))))))))

(deftest dispatch!-test
  ;; TODO: tests for error handling?
  (testing "enriches commands and delegates to the real dispatcher"
    (testutil/with-user-id user-id
      (binding [dispatcher/command! (fn [_conn _state command]
                                      {:spy command})]
        (is (= {:spy {:command/user user-id
                      :command/time test-time
                      :command/type :dummy.command/foo
                      :congregation/id cong-id}}
               (dmz/dispatch! {:command/type :dummy.command/foo
                               :congregation/id cong-id})))))))


;;;; Congregations

(deftest get-congregation-test
  (let [expected {:congregation/id cong-id
                  :congregation/name "Cong1 Name"
                  :congregation/timezone testdata/timezone-helsinki
                  :congregation/loans-csv-url "https://docs.google.com/spreadsheets/123"
                  :congregation/schema-name "cong1_schema"}
        demo-expected {:congregation/id "demo" ; changed
                       :congregation/name "Demo Congregation" ; changed
                       :congregation/timezone testdata/timezone-helsinki
                       #_:congregation/loans-csv-url ; removed
                       :congregation/schema-name nil}] ; removed

    (testutil/with-user-id user-id
      (testing "full permissions"
        (is (= expected (dmz/get-congregation cong-id)))))

    (testutil/with-super-user
      (testing "super user"
        (is (= expected (dmz/get-congregation cong-id)))))

    (testutil/with-user-id (UUID. 0 0x666)
      (testing "no permissions"
        (is (thrown-match? ExceptionInfo access-denied
                           (dmz/get-congregation cong-id)))))

    (testutil/with-anonymous-user
      (testing "anonymous"
        (is (thrown-match? ExceptionInfo not-logged-in
                           (dmz/get-congregation cong-id))))

      (testing "demo congregation"
        (is (= demo-expected (dmz/get-congregation "demo"))))

      (testing "opened a share"
        (binding [dmz/*state* (apply-share-opened dmz/*state*)]
          (is (= expected (dmz/get-congregation cong-id))))))))

(deftest list-congregations-test
  (let [expected [{:congregation/id cong-id
                   :congregation/name "Cong1 Name"
                   :congregation/timezone testdata/timezone-helsinki
                   :congregation/loans-csv-url "https://docs.google.com/spreadsheets/123"
                   :congregation/schema-name "cong1_schema"}]]

    (testutil/with-user-id user-id
      (testing "full permissions"
        (is (= expected (dmz/list-congregations)))))

    (testutil/with-super-user
      (testing "super user"
        (is (= expected (dmz/list-congregations)))))

    (testutil/with-user-id (UUID. 0 0x666)
      (testing "no permissions"
        (is (empty? (dmz/list-congregations)))))

    (testutil/with-anonymous-user
      (testing "anonymous"
        (is (empty? (dmz/list-congregations))))

      (testing "demo congregation"
        (is (empty? (dmz/list-congregations))))

      (testing "opened a share"
        (binding [dmz/*state* (apply-share-opened dmz/*state*)]
          (is (empty? (dmz/list-congregations))))))))

(deftest milestones-test
  (let [expected {:congregation-boundary-created test-time
                  :congregation-created test-time
                  :region-created test-time
                  :share-link-created test-time
                  :territory-assigned test-time
                  :territory-created test-time}
        ;; The generated demo events don't have an :event/time, so all completed milestones have a nil value.
        ;; Unfinished milestones don't even have a key, so there is no ambiguity; just check whether a key exists.
        demo-expected {:congregation-boundary-created nil
                       :congregation-created nil
                       :region-created nil
                       :territory-assigned nil
                       :territory-created nil}]

    (testutil/with-user-id user-id
      (testing "full permissions"
        (is (= expected (dmz/milestones cong-id)))))

    (testutil/with-super-user
      (testing "super user"
        (is (= expected (dmz/milestones cong-id)))))

    (testutil/with-user-id (UUID. 0 0x666)
      (testing "no permissions"
        (is (empty? (dmz/milestones cong-id)))))

    (testutil/with-anonymous-user
      (testing "anonymous"
        (is (empty? (dmz/milestones cong-id))))

      (testing "demo congregation"
        (is (= demo-expected (dmz/milestones "demo"))))

      (testing "opened a share"
        (binding [dmz/*state* (apply-share-opened dmz/*state*)]
          (is (empty? (dmz/milestones cong-id))))))))


;;;; Settings

(deftest list-congregation-users-test
  (let [expected [{:user/id user-id
                   :user/subject "user1"
                   :user/attributes {:name "User One"
                                     :email "user1@example.com"}}
                  {:user/id user-id2
                   :user/subject "user2"
                   :user/attributes {:name "User Two"
                                     :email "user2@example.com"}}]
        fake-get-users (fn [_conn query]
                         (is (= {:ids [user-id user-id2]} query)
                             "get-users query")
                         expected)]

    (binding [territory-bro.infra.user/get-users fake-get-users]
      (testutil/with-user-id user-id
        (testing "full permissions"
          (is (= expected (dmz/list-congregation-users cong-id)))))

      (testutil/with-super-user
        (testing "super user"
          (is (= expected (dmz/list-congregation-users cong-id)))))

      (testutil/with-user-id (UUID. 0 0x666)
        (testing "no permissions"
          (is (empty? (dmz/list-congregation-users cong-id)))))

      (testutil/with-anonymous-user
        (testing "anonymous"
          (is (empty? (dmz/list-congregation-users cong-id))))

        (testing "demo congregation"
          (is (empty? (dmz/list-congregation-users cong-id))))

        (testing "opened a share"
          (binding [dmz/*state* (apply-share-opened dmz/*state*)]
            (is (empty? (dmz/list-congregation-users cong-id)))))))))

(deftest download-qgis-project-test
  (let [expected (match/all-of #"dbname='gis-db'"
                               #"host=gis\.example\.com"
                               #"user='username123'"
                               #"password='password123'"
                               #"table=\"cong1_schema\"\.\"territory\"")]

    (testutil/with-user-id user-id
      (testing "full permissions"
        (is (match? expected (dmz/download-qgis-project cong-id))))

      (testing "without GIS access"
        (binding [dmz/*state* (permissions/revoke dmz/*state* user-id [:gis-access cong-id])]
          (is (thrown-match? ExceptionInfo access-denied
                             (dmz/download-qgis-project cong-id))))))

    (testutil/with-super-user
      (testing "super user"
        (is (thrown-match? ExceptionInfo access-denied
                           (dmz/download-qgis-project cong-id)))))

    (testutil/with-user-id (UUID. 0 0x666)
      (testing "no permissions"
        (is (thrown-match? ExceptionInfo access-denied
                           (dmz/download-qgis-project cong-id)))))

    (testutil/with-anonymous-user
      (testing "anonymous"
        (is (thrown-match? ExceptionInfo not-logged-in
                           (dmz/download-qgis-project cong-id))))

      (testing "demo congregation"
        (is (thrown-match? ExceptionInfo not-logged-in
                           (dmz/download-qgis-project cong-id))))

      (testing "opened a share"
        (binding [dmz/*state* (apply-share-opened dmz/*state*)]
          (is (thrown-match? ExceptionInfo not-logged-in
                             (dmz/download-qgis-project cong-id))))))))


;;;; Publishers

(deftest list-publishers-test
  (let [expected [test-publisher]]

    (testutil/with-user-id user-id
      (testing "full permissions"
        (is (= expected (dmz/list-publishers cong-id)))))

    (testutil/with-super-user
      (testing "super user"
        (is (= expected (dmz/list-publishers cong-id)))))

    (testutil/with-user-id (UUID. 0 0x666)
      (testing "no permissions"
        (is (nil? (dmz/list-publishers cong-id)))))

    (testutil/with-anonymous-user
      (testing "anonymous"
        (is (nil? (dmz/list-publishers cong-id))))

      (testing "demo congregation"
        (let [publishers (dmz/list-publishers "demo")]
          (is (not (empty? publishers)))
          (is (= demo/publishers publishers))
          (doseq [publisher publishers]
            (is (= #{:congregation/id :publisher/id :publisher/name} (set (keys publisher))))
            (is (= "demo" (:congregation/id publisher)))
            (is (uuid? (:publisher/id publisher)))
            (is (not (str/blank? (:publisher/name publisher)))))))

      (testing "opened a share"
        (binding [dmz/*state* (apply-share-opened dmz/*state*)]
          (is (nil? (dmz/list-publishers cong-id))))))))

(deftest get-publisher-test
  (let [expected test-publisher]

    (testutil/with-user-id user-id
      (testing "full permissions"
        (is (= expected (dmz/get-publisher cong-id publisher-id)))))

    (testutil/with-super-user
      (testing "super user"
        (is (= expected (dmz/get-publisher cong-id publisher-id)))))

    (testutil/with-user-id (UUID. 0 0x666)
      (testing "no permissions"
        (is (nil? (dmz/get-publisher cong-id publisher-id)))))

    (testutil/with-anonymous-user
      (testing "anonymous"
        (is (nil? (dmz/get-publisher cong-id publisher-id))))

      (testing "demo congregation"
        (let [publisher-id (:publisher/id (first demo/publishers))
              publisher (dmz/get-publisher "demo" publisher-id)]
          (is (uuid? publisher-id))
          (is (some? publisher))
          (is (= "demo" (:congregation/id publisher)))
          (is (= publisher-id (:publisher/id publisher)))
          (is (not (str/blank? (:publisher/name publisher))))))

      (testing "opened a share"
        (binding [dmz/*state* (apply-share-opened dmz/*state*)]
          ;; When a publisher opens a shared link, it is beneficial for them to see
          ;; if the territory has accidentally been assigned to someone other than them.
          ;; They should, however, not be allowed to see the full assignment history.
          ;; That permission check needs to be tightened in the caller of dmz/get-publisher.
          (is (= expected (dmz/get-publisher cong-id publisher-id))))))))


;;;; Territories

(deftest get-territory-test
  (let [expected {:congregation/id cong-id
                  :territory/id territory-id
                  :territory/number "123"
                  :territory/addresses "the addresses"
                  :territory/region "the region"
                  :territory/meta {:foo "bar"}
                  :territory/location testdata/wkt-helsinki-rautatientori
                  :territory/current-assignment {:assignment/id assignment-id
                                                 :assignment/start-date start-date
                                                 :assignment/covered-dates #{covered-date}
                                                 :publisher/id publisher-id
                                                 :publisher/name "John Doe"}
                  :territory/last-covered covered-date}
        demo-expected {:congregation/id "demo" ; changed
                       :territory/id territory-id
                       :territory/number "123"
                       :territory/addresses "the addresses"
                       :territory/region "the region"
                       :territory/meta {:foo "bar"}
                       :territory/location testdata/wkt-helsinki-rautatientori
                       :territory/current-assignment demo-current-assignment
                       :territory/last-covered demo-last-covered}]

    (testutil/with-user-id user-id
      (testing "full permissions"
        (is (= expected (dmz/get-territory cong-id territory-id)))))

    (testutil/with-super-user
      (testing "super user"
        (is (= expected (dmz/get-territory cong-id territory-id)))))

    (testutil/with-user-id (UUID. 0 0x666)
      (testing "no permissions"
        (is (thrown-match? ExceptionInfo access-denied
                           (dmz/get-territory cong-id territory-id)))))

    (testutil/with-anonymous-user
      (testing "anonymous"
        (is (thrown-match? ExceptionInfo not-logged-in
                           (dmz/get-territory cong-id territory-id))))

      (testing "demo congregation"
        (is (= demo-expected (dmz/get-territory "demo" territory-id))))

      (testing "opened a share"
        (binding [dmz/*state* (apply-share-opened dmz/*state*)]
          (is (= expected (dmz/get-territory cong-id territory-id))))))))

(deftest get-do-not-calls-test
  (let [expected "the do-not-calls"]

    (testutil/with-user-id user-id
      (testing "full permissions"
        (is (= expected (dmz/get-do-not-calls cong-id territory-id)))))

    (testutil/with-super-user
      (testing "super user"
        (is (= expected (dmz/get-do-not-calls cong-id territory-id)))))

    (testutil/with-user-id (UUID. 0 0x666)
      (testing "no permissions"
        (is (nil? (dmz/get-do-not-calls cong-id territory-id)))))

    (testutil/with-anonymous-user
      (testing "anonymous"
        (is (nil? (dmz/get-do-not-calls cong-id territory-id))))

      (testing "demo congregation"
        (is (nil? (dmz/get-do-not-calls "demo" territory-id))))

      (testing "opened a share"
        (binding [dmz/*state* (apply-share-opened dmz/*state*)]
          (is (= expected (dmz/get-do-not-calls cong-id territory-id))))))))

(deftest list-territories-test
  (let [expected [{:congregation/id cong-id
                   :territory/id territory-id
                   :territory/number "123"
                   :territory/addresses "the addresses"
                   :territory/region "the region"
                   :territory/meta {:foo "bar"}
                   :territory/location testdata/wkt-helsinki-rautatientori
                   :territory/current-assignment {:assignment/id assignment-id
                                                  :assignment/start-date start-date
                                                  :assignment/covered-dates #{covered-date}
                                                  :publisher/id publisher-id
                                                  :publisher/name "John Doe"}
                   :territory/last-covered covered-date}
                  {:congregation/id cong-id
                   :territory/id territory-id2
                   :territory/number "456"
                   :territory/addresses "the addresses"
                   :territory/region "the region"
                   :territory/meta {:foo "bar"}
                   :territory/location testdata/wkt-helsinki-kauppatori}]
        demo-expected (-> expected
                          (replace-in [0 :congregation/id] cong-id "demo")
                          (replace-in [1 :congregation/id] cong-id "demo")
                          (assoc-in [0 :territory/current-assignment] demo-current-assignment)
                          (replace-in [0 :territory/last-covered] covered-date demo-last-covered))]

    (testutil/with-user-id user-id
      (testing "full permissions"
        (is (= expected (dmz/list-territories cong-id)))))

    (testutil/with-super-user
      (testing "super user"
        (is (= expected (dmz/list-territories cong-id)))))

    (testutil/with-user-id (UUID. 0 0x666)
      (testing "no permissions"
        (is (empty? (dmz/list-territories cong-id)))))

    (testutil/with-anonymous-user
      (testing "anonymous"
        (is (empty? (dmz/list-territories cong-id))))

      (testing "demo congregation"
        (is (= demo-expected (dmz/list-territories "demo"))))

      (testing "opened a share"
        (binding [dmz/*state* (apply-share-opened dmz/*state*)]
          (is (= (take 1 expected) (dmz/list-territories cong-id))
              "can view only the shared territory, and no others"))))))

(deftest enrich-territory-loans-test
  (let [territories [{:territory/id territory-id
                      :territory/number "123"}
                     {:territory/id territory-id2
                      :territory/number "456"}]
        expected [{:territory/id territory-id
                   :territory/number "123"
                   :territory/loaned? true
                   :territory/staleness 7}
                  {:territory/id territory-id2
                   :territory/number "456"
                   :territory/loaned? false
                   :territory/staleness 8}]]

    (binding [loan/download! (constantly (str "Number,Loaned,Staleness\n"
                                              "123,TRUE,7\n"
                                              "456,FALSE,8\n"))]
      (testutil/with-user-id user-id
        (testing "full permissions"
          (is (= expected (dmz/enrich-territory-loans cong-id territories)))))

      (testutil/with-super-user
        (testing "super user"
          (is (= expected (dmz/enrich-territory-loans cong-id territories)))))

      (testutil/with-user-id (UUID. 0 0x666)
        (testing "no permissions"
          (is (= territories (dmz/enrich-territory-loans cong-id territories)))))

      (testutil/with-anonymous-user
        (testing "anonymous"
          (is (= territories (dmz/enrich-territory-loans cong-id territories))))

        (testing "demo congregation"
          (is (= territories (dmz/enrich-territory-loans "demo" territories))))

        (testing "opened a share"
          (binding [dmz/*state* (apply-share-opened dmz/*state*)]
            (is (= territories (dmz/enrich-territory-loans cong-id territories)))))))))

(deftest get-territory-assignment-history-test
  (let [expected [{:assignment/id assignment-id
                   :assignment/start-date start-date
                   :assignment/covered-dates #{covered-date}
                   :publisher/id publisher-id
                   :publisher/name "John Doe"}]]

    (testutil/with-user-id user-id
      (testing "full permissions"
        (is (= expected (dmz/get-territory-assignment-history cong-id territory-id)))))

    (testutil/with-super-user
      (testing "super user"
        (is (= expected (dmz/get-territory-assignment-history cong-id territory-id)))))

    (testutil/with-user-id (UUID. 0 0x666)
      (testing "no permissions"
        (is (thrown-match? ExceptionInfo access-denied
                           (dmz/get-territory-assignment-history cong-id territory-id)))))

    (testutil/with-anonymous-user
      (testing "anonymous"
        (is (thrown-match? ExceptionInfo not-logged-in
                           (dmz/get-territory-assignment-history cong-id territory-id))))

      (testing "demo congregation"
        (let [assignments (dmz/get-territory-assignment-history "demo" territory-id)]
          (is (< 3 (count assignments)))
          (is (= demo-current-assignment (last assignments)))))

      (testing "opened a share"
        (binding [dmz/*state* (apply-share-opened dmz/*state*)]
          (is (nil? (dmz/get-territory-assignment-history cong-id territory-id))))))))


;;;; Shares

(def create-share-command
  {:command/type :share.command/create-share
   :command/user user-id
   :congregation/id cong-id
   :territory/id territory-id
   :share/key share-key})

(deftest share-territory-link-test
  (let [expected {:key share-key
                  :url "https://example.com/share/abc123/123"}
        expected-command (assoc create-share-command :share/type :link)
        expected-demo {:key demo-share-key
                       :url "https://example.com/share/demo-AAAAAAAAAAAAAAAAAAAABA/123"}]

    (testutil/with-user-id user-id
      (testing "full permissions"
        (binding [share/generate-share-key (constantly share-key)]
          (with-fixtures [fake-dispatcher-fixture]
            (is (= expected (dmz/share-territory-link cong-id territory-id)))
            (is (= expected-command (dissoc @*last-command :command/time :share/id))))))

      (testing "without share permission"
        (binding [dmz/*state* (permissions/revoke dmz/*state* user-id [:share-territory-link cong-id])]
          (is (thrown-match? ExceptionInfo access-denied
                             (dmz/share-territory-link cong-id territory-id))))))

    (testutil/with-super-user
      (testing "super user"
        (is (thrown-match? ExceptionInfo access-denied
                           (dmz/share-territory-link cong-id territory-id)))))

    (testutil/with-user-id (UUID. 0 0x666)
      (testing "no permissions"
        (is (thrown-match? ExceptionInfo access-denied
                           (dmz/share-territory-link cong-id territory-id)))))

    (testutil/with-anonymous-user
      (testing "anonymous"
        (is (thrown-match? ExceptionInfo not-logged-in
                           (dmz/share-territory-link cong-id territory-id))))

      (testing "demo congregation"
        (is (= expected-demo (dmz/share-territory-link "demo" territory-id))))

      (testing "opened a share"
        (binding [dmz/*state* (apply-share-opened dmz/*state*)]
          (is (thrown-match? ExceptionInfo not-logged-in
                             (dmz/share-territory-link cong-id territory-id))))))))

(deftest generate-qr-code-test
  (let [expected {:key share-key
                  :url "https://qr.example.com/abc123"}
        expected-command (assoc create-share-command :share/type :qr-code)
        expected-demo {:key demo-share-key
                       :url "https://qr.example.com/demo-AAAAAAAAAAAAAAAAAAAABA"}]

    (testutil/with-user-id user-id
      (testing "full permissions"
        (binding [share/generate-share-key (constantly share-key)]
          (with-fixtures [fake-dispatcher-fixture]
            (is (= expected (dmz/generate-qr-code cong-id territory-id)))
            (is (= expected-command (dissoc @*last-command :command/time :share/id))))))

      (testing "without share permission"
        (binding [dmz/*state* (permissions/revoke dmz/*state* user-id [:share-territory-link cong-id])]
          (is (thrown-match? ExceptionInfo access-denied
                             (dmz/generate-qr-code cong-id territory-id))))))

    (testutil/with-super-user
      (testing "super user"
        (is (thrown-match? ExceptionInfo access-denied
                           (dmz/generate-qr-code cong-id territory-id)))))

    (testutil/with-user-id (UUID. 0 0x666)
      (testing "no permissions"
        (is (thrown-match? ExceptionInfo access-denied
                           (dmz/generate-qr-code cong-id territory-id)))))

    (testutil/with-anonymous-user
      (testing "anonymous"
        (is (thrown-match? ExceptionInfo not-logged-in
                           (dmz/generate-qr-code cong-id territory-id))))

      (testing "demo congregation"
        (is (= expected-demo (dmz/generate-qr-code "demo" territory-id))))

      (testing "opened a share"
        (binding [dmz/*state* (apply-share-opened dmz/*state*)]
          (is (thrown-match? ExceptionInfo not-logged-in
                             (dmz/generate-qr-code cong-id territory-id))))))))

(deftest open-share!-test
  (let [session {::unrelated "session data"}
        expected [{:share/id share-id
                   :share/type :link
                   :congregation/id cong-id
                   :territory/id territory-id}
                  {::dmz/opened-shares #{share-id}
                   ::unrelated "session data"}]
        expected-command {:command/type :share.command/record-share-opened
                          :command/user auth/anonymous-user-id
                          :share/id share-id}
        demo-expected [{:congregation/id "demo"
                        :territory/id territory-id}
                       nil]]

    (testutil/with-anonymous-user
      (testing "open normal share"
        (with-fixtures [fake-dispatcher-fixture]
          (is (= expected (dmz/open-share! share-key session)))
          (is (= expected-command (dissoc @*last-command :command/time))
              "records that the share was opened")))

      (testing "open demo share"
        (is (= demo-expected (dmz/open-share! demo-share-key session))))

      (testing "invalid link"
        (is (nil? (dmz/open-share! "xyz" session)))))))

(deftest open-share-without-cookies-test
  (testutil/with-anonymous-user
    (is (not (dmz/allowed? [:view-congregation-temporarily cong-id])))
    (is (not (dmz/allowed? [:view-territory cong-id territory-id])))

    (testing "grants permission when share exists"
      (binding [dmz/*state* (dmz/open-share-without-cookies dmz/*state* cong-id territory-id share-key)]
        (is (dmz/allowed? [:view-congregation-temporarily cong-id]))
        (is (dmz/allowed? [:view-territory cong-id territory-id]))))

    (let [original dmz/*state*]
      (testing "ignored if cong-id is wrong"
        (is (= original (dmz/open-share-without-cookies original (UUID. 0 0x666) territory-id share-key))))

      (testing "ignored if territory-id is wrong"
        (is (= original (dmz/open-share-without-cookies original cong-id (UUID. 0 0x666) share-key))))

      (testing "ignored if share-key is wrong"
        (is (= original (dmz/open-share-without-cookies original cong-id territory-id "666")))))))


;;;; Other geometries

(deftest get-congregation-boundary-test
  (let [expected testdata/wkt-helsinki]

    (testutil/with-user-id user-id
      (testing "full permissions"
        (is (= expected (dmz/get-congregation-boundary cong-id)))))

    (testutil/with-super-user
      (testing "super user"
        (is (= expected (dmz/get-congregation-boundary cong-id)))))

    (testutil/with-user-id (UUID. 0 0x666)
      (testing "no permissions"
        (is (nil? (dmz/get-congregation-boundary cong-id)))))

    (testutil/with-anonymous-user
      (testing "anonymous"
        (is (nil? (dmz/get-congregation-boundary cong-id))))

      (testing "demo congregation"
        (is (= expected (dmz/get-congregation-boundary "demo"))))

      (testing "opened a share"
        (binding [dmz/*state* (apply-share-opened dmz/*state*)]
          (is (nil? (dmz/get-congregation-boundary cong-id))))))))

(deftest list-regions-test
  (let [expected [{:region/id region-id
                   :region/name "the name"
                   :region/location testdata/wkt-south-helsinki}]]

    (testutil/with-user-id user-id
      (testing "full permissions"
        (is (= expected (dmz/list-regions cong-id)))))

    (testutil/with-super-user
      (testing "super user"
        (is (= expected (dmz/list-regions cong-id)))))

    (testutil/with-user-id (UUID. 0 0x666)
      (testing "no permissions"
        (is (nil? (dmz/list-regions cong-id)))))

    (testutil/with-anonymous-user
      (testing "anonymous"
        (is (nil? (dmz/list-regions cong-id))))

      (testing "demo congregation"
        (is (= expected (dmz/list-regions "demo"))))

      (testing "opened a share"
        (binding [dmz/*state* (apply-share-opened dmz/*state*)]
          (is (nil? (dmz/list-regions cong-id))))))))

(deftest list-card-minimap-viewports-test
  (let [expected [{:card-minimap-viewport/id card-minimap-viewport-id
                   :card-minimap-viewport/location testdata/wkt-polygon}]]

    (testutil/with-user-id user-id
      (testing "full permissions"
        (is (= expected (dmz/list-card-minimap-viewports cong-id)))))

    (testutil/with-super-user
      (testing "super user"
        (is (= expected (dmz/list-card-minimap-viewports cong-id)))))

    (testutil/with-user-id (UUID. 0 0x666)
      (testing "no permissions"
        (is (nil? (dmz/list-card-minimap-viewports cong-id)))))

    (testutil/with-anonymous-user
      (testing "anonymous"
        (is (nil? (dmz/list-card-minimap-viewports cong-id))))

      (testing "demo congregation"
        (is (= expected (dmz/list-card-minimap-viewports "demo"))))

      (testing "opened a share"
        (binding [dmz/*state* (apply-share-opened dmz/*state*)]
          (is (nil? (dmz/list-card-minimap-viewports cong-id))))))))
