;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.territory-page-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [net.cgrand.enlive-html :as en]
            [territory-bro.domain.congregation :as congregation]
            [territory-bro.domain.do-not-calls :as do-not-calls]
            [territory-bro.domain.do-not-calls-test :as do-not-calls-test]
            [territory-bro.domain.share :as share]
            [territory-bro.domain.testdata :as testdata]
            [territory-bro.infra.config :as config]
            [territory-bro.test.fixtures :refer :all]
            [territory-bro.test.testutil :as testutil :refer [replace-in]]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.map-interaction-help-test :as map-interaction-help-test]
            [territory-bro.ui.territory-page :as territory-page])
  (:import (java.util UUID)))

(def cong-id (UUID. 0 1))
(def territory-id (UUID. 0 2))
(def user-id (UUID. 0 3))
(def model
  {:congregation {:congregation/name "Congregation 1"}
   :territory {:territory/id territory-id
               :territory/number "123"
               :territory/addresses "the addresses"
               :territory/region "the region"
               :territory/meta {:foo "bar"}
               :territory/location testdata/wkt-helsinki-rautatientori
               :territory/do-not-calls "the do-not-calls"}
   :permissions {:edit-do-not-calls true
                 :share-territory-link true}
   :mac? false})
(def demo-model ; the important difference is hiding do-not-calls, to avoid accidental PII leaks
  {:congregation {:congregation/name "Demo Congregation"}
   :territory {:territory/id territory-id
               :territory/number "123"
               :territory/addresses "the addresses"
               :territory/region "the region"
               :territory/meta {:foo "bar"}
               :territory/location testdata/wkt-helsinki-rautatientori
               :territory/do-not-calls nil}
   :permissions {:edit-do-not-calls false
                 :share-territory-link true}
   :mac? false})

(def test-events
  (flatten [{:event/type :congregation.event/congregation-created
             :congregation/id cong-id
             :congregation/name "Congregation 1"
             :congregation/schema-name "cong1_schema"}
            (congregation/admin-permissions-granted cong-id user-id)
            {:event/type :territory.event/territory-defined
             :congregation/id cong-id
             :territory/id territory-id
             :territory/number "123"
             :territory/addresses "the addresses"
             :territory/region "the region"
             :territory/meta {:foo "bar"}
             :territory/location testdata/wkt-helsinki-rautatientori}]))

(deftest model!-test
  (let [request {:path-params {:congregation cong-id
                               :territory territory-id}}]
    (testutil/with-events test-events
      (binding [do-not-calls/get-do-not-calls do-not-calls-test/fake-get-do-not-calls]
        (testutil/with-user-id user-id

          (testing "default"
            (is (= model (territory-page/model! request))))

          (testing "demo congregation"
            (binding [config/env {:demo-congregation cong-id}]
              (let [request (replace-in request [:path-params :congregation] cong-id "demo")]
                (is (= demo-model
                       (territory-page/model! request)
                       (testutil/with-anonymous-user
                         (territory-page/model! request))))))))))))

(deftest view-test
  (testing "full permissions"
    (is (= (html/normalize-whitespace
            "Territory 123

             Number
               123
             Region
               the region
             Addresses
               the addresses
             Do-not-calls
               Edit
               the do-not-calls

             {share.svg} Share a link"
            map-interaction-help-test/default-visible-text)
           (-> (territory-page/view model)
               html/visible-text))))

  (testing "minimum permissions"
    (let [model (-> model
                    (replace-in [:permissions :edit-do-not-calls] true false)
                    (replace-in [:permissions :share-territory-link] true false))]
      (is (= (html/normalize-whitespace
              "Territory 123

               Number
                 123
               Region
                 the region
               Addresses
                 the addresses
               Do-not-calls
                 the do-not-calls"
              map-interaction-help-test/default-visible-text)
             (-> (territory-page/view model)
                 html/visible-text))))))

(defn parse-open-graph-tags [html]
  (->> (en/select (en/html-snippet (str html)) [:meta])
       (map :attrs)
       (reduce (fn [m {:keys [property content]}]
                 (assoc m property content))
               {})))

(deftest head-test
  (testing "has Open Graph tags for shared link preview"
    (is (= {"og:type" "website"
            "og:title" "Territory 123 - the region - Congregation 1"
            "og:description" "the addresses"
            "og:image" "https://tile.openstreetmap.org/16/37308/18969.png"}
           (-> (territory-page/head model)
               (parse-open-graph-tags)))))

  (testing "region is optional"
    (is (= "Territory 123 - Congregation 1"
           (-> (territory-page/head (replace-in model [:territory :territory/region] "the region" ""))
               (parse-open-graph-tags)
               (get "og:title")))))

  (testing "formats addresses into one line" ; WhatsApp shows only the first line and truncates the rest
    (is (= "A, B, C"
           (-> (territory-page/head (replace-in model [:territory :territory/addresses] "the addresses" "A \n B\n\nC\n"))
               (parse-open-graph-tags)
               (get "og:description"))))))


;;;; Components and helpers

(deftest do-not-calls-test
  (testing "viewing"
    (is (= (html/normalize-whitespace
            "Edit
             the do-not-calls")
           (-> (territory-page/do-not-calls--viewing model)
               html/visible-text))))

  (testing "editing"
    (is (= (html/normalize-whitespace
            "the do-not-calls
             Save")
           (-> (territory-page/do-not-calls--editing model)
               html/visible-text)))))

(deftest do-not-calls--save!-test
  (let [request {:path-params {:congregation cong-id
                               :territory territory-id}
                 :params {:do-not-calls "the new value"}}]
    (testutil/with-events test-events
      (binding [do-not-calls/get-do-not-calls do-not-calls-test/fake-get-do-not-calls]
        (testutil/with-user-id user-id
          (with-fixtures [fake-dispatcher-fixture]

            (let [html (territory-page/do-not-calls--save! request)]
              (is (= {:command/type :do-not-calls.command/save-do-not-calls
                      :command/user user-id
                      :congregation/id cong-id
                      :territory/id territory-id
                      :territory/do-not-calls "the new value"}
                     (dissoc @*last-command :command/time)))
              (is (-> (html/visible-text html)
                      (str/includes? "Edit"))))))))))

(deftest share-link-test
  (testing "closed"
    (is (= "{share.svg} Share a link"
           (-> (territory-page/share-link {:open? false})
               html/visible-text))))

  (testing "open"
    (is (= (html/normalize-whitespace
            "{share.svg} Share a link
             {close.svg}
             People with this link will be able to view this territory map without logging in:
             [https://territorybro.com/link] {copy.svg}")
           (-> (territory-page/share-link {:open? true
                                           :link "https://territorybro.com/link"})
               html/visible-text)))))

(deftest share-link--open!-test
  (let [request {:path-params {:congregation cong-id
                               :territory territory-id}}]
    (testutil/with-events test-events
      (binding [share/generate-share-key (constantly "abcxyz")]
        (testutil/with-user-id user-id
          (with-fixtures [fake-dispatcher-fixture]

            (let [html (territory-page/share-link--open! request)]
              (is (= {:command/type :share.command/create-share
                      :command/user user-id
                      :congregation/id cong-id
                      :territory/id territory-id
                      :share/type :link
                      :share/key "abcxyz"}
                     (dissoc @*last-command :command/time :share/id)))
              (is (-> (html/visible-text html)
                      (str/includes? "[/share/abcxyz/123] {copy.svg}"))))))))))
