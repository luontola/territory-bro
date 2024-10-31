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
  (:import (java.time LocalDate LocalTime OffsetDateTime ZoneOffset)
           (java.util UUID)))

(def cong-id (UUID. 0 1))
(def territory-id (UUID. 0 2))
(def assignment-id (UUID. 0 3))
(def publisher-id (UUID. 0 4))
(def user-id (UUID. 0 5))
(def start-date (LocalDate/of 2000 1 1))
(def end-date (LocalDate/of 2000 2 1))
(def today (LocalDate/of 2000 3 1))
(def model
  {:congregation {:congregation/name "Congregation 1"}
   :territory {:territory/id territory-id
               :territory/number "123"
               :territory/addresses "the addresses"
               :territory/region "the region"
               :territory/meta {:foo "bar"}
               :territory/location testdata/wkt-helsinki-rautatientori
               :territory/do-not-calls "the do-not-calls"}
   :assignment-history []
   :today today
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
   :assignment-history []
   :today today
   :permissions {:edit-do-not-calls false
                 :share-territory-link true}
   :mac? false})

(def untouched-model model)
(def assigned-model
  (let [assignment {:assignment/id assignment-id
                    :assignment/start-date start-date
                    :publisher/id publisher-id
                    :publisher/name "John Doe"}]
    (-> model
        (update :territory assoc :territory/current-assignment assignment)
        (assoc :assignment-history [assignment]))))
(def returned-model
  (let [assignment {:assignment/id assignment-id
                    :assignment/start-date start-date
                    :assignment/covered-dates #{end-date}
                    :assignment/end-date end-date
                    :publisher/id publisher-id
                    :publisher/name "John Doe"}]
    (-> model
        (update :territory assoc :territory/last-covered end-date)
        (assoc :assignment-history [assignment]))))

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
   :assignment/covered-date end-date})
(def territory-returned
  {:event/type :territory.event/territory-returned
   :congregation/id cong-id
   :territory/id territory-id
   :assignment/id assignment-id
   :assignment/end-date end-date})

(def test-time (.toInstant (OffsetDateTime/of today LocalTime/NOON ZoneOffset/UTC)))
(use-fixtures :once (fixed-clock-fixture test-time))

(deftest model!-test
  (let [request {:path-params {:congregation cong-id
                               :territory territory-id}}]
    (testutil/with-events test-events
      (binding [do-not-calls/get-do-not-calls do-not-calls-test/fake-get-do-not-calls]
        (testutil/with-user-id user-id

          (testing "default"
            (is (= model (territory-page/model! request))))

          (testing "assigned"
            (testutil/with-events [territory-assigned]
              (is (= assigned-model (territory-page/model! request)))))

          (testing "returned & covered"
            (testutil/with-events [territory-assigned territory-covered territory-returned]
              (is (= returned-model (territory-page/model! request)))))

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

(deftest months-difference-test
  (testing "same day"
    (is (= 0 (territory-page/months-difference (LocalDate/of 2000 1 1) (LocalDate/of 2000 1 1)))))

  (testing "start of month"
    (is (= 0 (territory-page/months-difference (LocalDate/of 2000 1 1) (LocalDate/of 2000 1 31))))
    (is (= 1 (territory-page/months-difference (LocalDate/of 2000 1 1) (LocalDate/of 2000 2 1)))))

  (testing "middle of month"
    (is (= 0 (territory-page/months-difference (LocalDate/of 2000 1 15) (LocalDate/of 2000 2 14))))
    (is (= 1 (territory-page/months-difference (LocalDate/of 2000 1 15) (LocalDate/of 2000 2 15)))))

  (testing "over a year"
    (is (= 11 (territory-page/months-difference (LocalDate/of 2000 1 1) (LocalDate/of 2000 12 1))))
    (is (= 12 (territory-page/months-difference (LocalDate/of 2000 1 1) (LocalDate/of 2001 1 1))))
    (is (= 18 (territory-page/months-difference (LocalDate/of 2000 1 1) (LocalDate/of 2001 7 1)))))

  (testing "negative"
    (is (= -1 (territory-page/months-difference (LocalDate/of 2000 2 1) (LocalDate/of 2000 1 1))))))

(deftest assignment-status-test
  (testing "untouched"
    (is (= (html/normalize-whitespace
            "Assign
             Up for grabs")
           (-> (territory-page/assignment-status untouched-model)
               html/visible-text))))

  (testing "assigned"
    (is (= (html/normalize-whitespace
            "Return
             Assigned to John Doe
             (2 months, since 2000-01-01)")
           (-> (territory-page/assignment-status assigned-model)
               html/visible-text))))

  (testing "returned"
    (is (= (html/normalize-whitespace
            "Assign
             Up for grabs
             (1 months, since 2000-02-01)")
           (-> (territory-page/assignment-status returned-model)
               html/visible-text)))))

(deftest assign-territory-dialog-test
  (is (= (html/normalize-whitespace
          "Assign territory
             Publisher []
             Date [2000-03-01]
           Assign territory
           Cancel")
         (-> (territory-page/assign-territory-dialog untouched-model)
             html/visible-text)
         (-> (territory-page/assign-territory-dialog returned-model)
             html/visible-text)))

  (testing "date cannot be in the future"
    (let [model (assoc assigned-model :today (LocalDate/of 2000 3 15))]
      (is (str/includes?
           (-> (territory-page/assign-territory-dialog model))
           " max=\"2000-03-15\"")))))

(deftest return-territory-dialog-test
  (is (= (html/normalize-whitespace
          "Return territory
             Date [2000-03-01]
             [true] Return the territory to storage
             [true] Mark the territory as covered
           Return territory
           Mark covered
           Cancel")
         (-> (territory-page/return-territory-dialog assigned-model)
             html/visible-text)))

  (testing "date cannot be in the future"
    (let [model (assoc assigned-model :today (LocalDate/of 2000 3 15))]
      (is (str/includes?
           (-> (territory-page/return-territory-dialog model))
           " max=\"2000-03-15\""))))

  (testing "date cannot be before start date"
    (let [model (assoc-in assigned-model [:territory :territory/current-assignment :assignment/start-date]
                          (LocalDate/of 2000 1 15))]
      (is (str/includes?
           (-> (territory-page/return-territory-dialog model))
           " min=\"2000-01-15\""))))

  (testing "date cannot be before previous covered dates"
    (let [model (assoc-in assigned-model [:territory :territory/current-assignment :assignment/covered-dates]
                          #{(LocalDate/of 2000 1 15)})]
      (is (str/includes?
           (-> (territory-page/return-territory-dialog model))
           " min=\"2000-01-15\"")))))

(deftest assignment-history-test
  (is (= (html/normalize-whitespace
          "Assignment history
                                               Edit
           2 months
           2024-08-25    ✅ Covered
           4 months
           2024-04-13    ⤴️ Assigned to John Doe
           8 months
                                               Edit
           2023-08-09    📥 Returned
                         ✅ Covered
           2 months
           2023-05-30    ⤴️ Assigned to Joe Blow")
         (-> (territory-page/assignment-history territory-page/fake-assignment-model-history)
             html/visible-text))))
