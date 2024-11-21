(ns territory-bro.ui.territory-list-page-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [reitit.core :as reitit]
            [territory-bro.domain.congregation :as congregation]
            [territory-bro.domain.demo :as demo]
            [territory-bro.domain.dmz :as dmz]
            [territory-bro.domain.loan :as loan]
            [territory-bro.domain.publisher :as publisher]
            [territory-bro.domain.testdata :as testdata]
            [territory-bro.infra.authentication :as auth]
            [territory-bro.test.fixtures :refer :all]
            [territory-bro.test.testutil :as testutil :refer [replace-in]]
            [territory-bro.ui :as ui]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.territory-list-page :as territory-list-page]
            [territory-bro.ui.territory-page :as territory-page])
  (:import (java.time LocalDate LocalTime OffsetDateTime ZoneOffset)
           (java.util UUID)))

(def user-id (random-uuid))
(def cong-id (random-uuid))
(def territory-id (UUID. 0 1))
(def publisher-id (UUID. 0 2))
(def assignment-id (UUID. 0 3))
(def start-date (LocalDate/of 2000 1 1))
(def end-date (LocalDate/of 2000 2 1))
(def today (LocalDate/of 2000 3 1))
(def publisher {:congregation/id cong-id
                :publisher/id publisher-id
                :publisher/name "John Doe"})

(def model
  {:congregation-boundary testdata/wkt-helsinki
   :territories [{:congregation/id cong-id
                  :territory/id territory-id
                  :territory/number "123"
                  :territory/addresses "the addresses"
                  :territory/region "the region"
                  :territory/meta {:foo "bar"}
                  :territory/location testdata/wkt-helsinki-rautatientori
                  :territory/loaned? false
                  :territory/staleness Integer/MAX_VALUE}]
   :has-loans? false
   :today today
   :permissions {:view-congregation-temporarily false}
   :sort-column :number
   :sort-reverse? false})
(def model-loans-enabled
  (replace-in model [:has-loans?] false true))
(def model-loans-fetched
  (update-in model-loans-enabled [:territories 0] merge {:territory/loaned? true
                                                         :territory/staleness 7}))
(def model-with-assignments
  (update-in model [:territories 0] merge {:territory/loaned? true ; TODO: legacy field, remove me
                                           :territory/staleness 2 ; TODO: legacy field, remove me
                                           :territory/last-covered end-date
                                           :territory/current-assignment {:assignment/id assignment-id
                                                                          :assignment/start-date start-date
                                                                          :assignment/covered-dates #{end-date}
                                                                          :publisher/id publisher-id
                                                                          :publisher/name "John Doe"}}))
(def model-with-returned-territory
  (update-in model [:territories 0] merge {:territory/loaned? false ; TODO: legacy field, remove me
                                           :territory/staleness 1 ; TODO: legacy field, remove me
                                           :territory/last-covered end-date}))

(def demo-model
  (-> model
      (replace-in [:territories 0 :congregation/id] cong-id "demo")
      (assoc :permissions {:view-congregation-temporarily false})))
(def anonymous-model
  (assoc model
         :congregation-boundary nil
         :permissions {:view-congregation-temporarily true}))

(def test-events
  (flatten [{:event/type :congregation.event/congregation-created
             :congregation/id cong-id
             :congregation/name "Congregation 1"
             :congregation/schema-name "cong1_schema"}
            (congregation/admin-permissions-granted cong-id user-id)
            {:event/type :congregation-boundary.event/congregation-boundary-defined
             :congregation/id cong-id
             :congregation-boundary/id (random-uuid)
             :congregation-boundary/location testdata/wkt-helsinki}
            {:event/type :territory.event/territory-defined
             :congregation/id cong-id
             :territory/id territory-id
             :territory/number "123"
             :territory/addresses "the addresses"
             :territory/region "the region"
             :territory/meta {:foo "bar"}
             :territory/location testdata/wkt-helsinki-rautatientori}]))
(def demo-events
  (concat [demo/congregation-created]
          (into [] (demo/transform-gis-events cong-id) test-events)))

(def fake-publishers {cong-id {publisher-id publisher}})

(defn fakes [f]
  (binding [publisher/publishers-by-id (fn [_conn cong-id]
                                         (get fake-publishers cong-id))]
    (f)))

(def test-time (.toInstant (OffsetDateTime/of today LocalTime/NOON ZoneOffset/UTC)))

(use-fixtures :once (join-fixtures [fakes (fixed-clock-fixture test-time)]))

(deftest model!-test
  (let [request {:path-params {:congregation cong-id}}]
    (testutil/with-events (concat test-events demo-events)
      (testutil/with-user-id user-id
        (testing "default"
          (is (= model (territory-list-page/model! request {}))))

        (testing "demo congregation"
          (let [request {:path-params {:congregation "demo"}}]
            (is (= demo-model
                   (territory-list-page/model! request {})
                   (testutil/with-anonymous-user
                     (territory-list-page/model! request {}))))))

        (testing "anonymous, has opened a share"
          (testutil/with-anonymous-user
            (let [share-id (random-uuid)
                  request (assoc request :session {::dmz/opened-shares #{share-id}})]
              (testutil/with-events [{:event/type :share.event/share-created
                                      :share/id share-id
                                      :share/key "share123"
                                      :share/type :link
                                      :congregation/id cong-id
                                      :territory/id territory-id}]
                (testutil/with-request-state request
                  (is (= anonymous-model
                         (territory-list-page/model! request {}))))))))

        (testing "territory assignment status"
          (testutil/with-events [{:event/type :territory.event/territory-assigned
                                  :congregation/id cong-id
                                  :territory/id territory-id
                                  :assignment/id assignment-id
                                  :assignment/start-date start-date
                                  :publisher/id publisher-id}
                                 {:event/type :territory.event/territory-covered
                                  :congregation/id cong-id
                                  :territory/id territory-id
                                  :assignment/id assignment-id
                                  :assignment/covered-date end-date}]
            (is (= model-with-assignments (territory-list-page/model! request {}))
                "territory assigned")

            (testutil/with-events [{:event/type :territory.event/territory-returned
                                    :congregation/id cong-id
                                    :territory/id territory-id
                                    :assignment/id assignment-id
                                    :assignment/end-date end-date}]
              (is (= model-with-returned-territory (territory-list-page/model! request {}))
                  "territory returned"))))

        (testing "loans enabled,"
          (testutil/with-events [{:event/type :congregation.event/settings-updated
                                  :congregation/id cong-id
                                  :congregation/loans-csv-url "https://docs.google.com/spreadsheets/1"}]
            (binding [loan/download! (constantly (str "Number,Loaned,Staleness\n"
                                                      "123,TRUE,7\n"))]
              (testing "not fetched"
                (is (= model-loans-enabled
                       (territory-list-page/model! request {:fetch-loans? false}))))

              (testing "fetched"
                (is (= model-loans-fetched
                       (territory-list-page/model! request {:fetch-loans? true})))))))

        (testing "sorting options"
          (let [request (assoc request :params {:sort "status"
                                                :reverse ""})]
            (is (= (-> model
                       (replace-in [:sort-column] :number :status)
                       (replace-in [:sort-reverse?] false true))
                   (territory-list-page/model! request {})))))))))

(deftest sort-territories-test
  (testing "sort by territory number"
    ;; Primary use case: find a territory by number.
    (let [expected [{:territory/number "1"}
                    {:territory/number "2"}
                    {:territory/number "3"}]]
      (is (= expected
             (territory-list-page/sort-territories :number false (shuffle expected))))
      (is (= (reverse expected)
             (territory-list-page/sort-territories :number true (shuffle expected)))
          "reverse")))

  (testing "sort by assignment status"
    ;; Primary use case: assign a territory, find vacant territories which haven't been covered for a long time.
    ;; Secondary use case: find overdue assigned territories, to get them covered faster.
    (let [expected [{:description "untouched"}
                    {:description "vacant, most stale"
                     :territory/last-covered (LocalDate/ofEpochDay 1)}
                    {:description "vacant"
                     :territory/last-covered (LocalDate/ofEpochDay 2)}
                    {:description "assigned"
                     :territory/current-assignment {:assignment/start-date (LocalDate/ofEpochDay 2)}}
                    {:description "assigned, most stale"
                     :territory/current-assignment {:assignment/start-date (LocalDate/ofEpochDay 1)}}]]
      (is (= expected
             (territory-list-page/sort-territories :status false (shuffle expected))))
      (is (= (reverse expected)
             (territory-list-page/sort-territories :status true (shuffle expected)))
          "reverse")))

  (testing "sort by last covered"
    ;; Primary use case: find territories which haven't been covered for a long time.
    ;; Could be either to assign them or to find overdue assigned territories.
    (let [expected [{:description "untouched"}
                    {:description "most stale"
                     :territory/last-covered (LocalDate/ofEpochDay 1)}
                    {:description "least stale"
                     :territory/last-covered (LocalDate/ofEpochDay 2)}]]
      (is (= expected
             (territory-list-page/sort-territories :covered false (shuffle expected))))
      (is (= (reverse expected)
             (territory-list-page/sort-territories :covered true (shuffle expected)))
          "reverse"))))

(deftest sortable-column-header-test
  (testing "sorted by another column"
    (let [html (territory-list-page/sortable-column-header "Label" :stuff {:sort-column :other
                                                                           :sort-reverse? false})]
      (is (= "Label" (html/visible-text html)))
      (is (str/includes? html "href=\"?sort=stuff\""))))

  (testing "sorted by current column"
    (let [html (territory-list-page/sortable-column-header "Label" :stuff {:sort-column :stuff
                                                                           :sort-reverse? false})]
      (is (= "Label ↑" (html/visible-text html)))
      (is (str/includes? html "href=\"?sort=stuff&amp;reverse\""))))

  (testing "sorted by current column in reverse"
    (let [html (territory-list-page/sortable-column-header "Label" :stuff {:sort-column :stuff
                                                                           :sort-reverse? true})]
      (is (= "Label ↓" (html/visible-text html)))
      (is (str/includes? html "href=\"?sort=stuff\"")))))

(deftest view-test
  (testing "default"
    (is (= (html/normalize-whitespace
            "Territories

             Search [] Clear
             Number ↑   Region       Addresses       Status         Last covered
             123        the region   the addresses   Up for grabs")
           (-> (territory-list-page/view model)
               html/visible-text))))

  (testing "shows territory assignment status"
    (is (= (html/normalize-whitespace
            "Territories

             Search [] Clear
             Number ↑   Region       Addresses       Status                              Last covered
             123        the region   the addresses   Assigned to John Doe for 2 months   1 months ago (2000-02-01)")
           (-> (territory-list-page/view model-with-assignments)
               html/visible-text))
        "territory assigned")
    (is (= (html/normalize-whitespace
            "Territories

             Search [] Clear
             Number ↑   Region       Addresses       Status         Last covered
             123        the region   the addresses   Up for grabs   1 months ago (2000-02-01)")
           (-> (territory-list-page/view model-with-returned-territory)
               html/visible-text))
        "territory returned"))

  (testing "placeholder for deleted publishers"
    (let [model (replace-in model-with-assignments [:territories 0 :territory/current-assignment :publisher/name] "John Doe" nil)]
      (is (str/includes?
           (-> (territory-list-page/view model)
               html/visible-text)
           "Assigned to [deleted] for 2 months"))))

  (testing "each row embeds the searchable text in lowercase"
    (let [model (-> model
                    (replace-in [:territories 0 :territory/number] "123" "123A")
                    (replace-in [:territories 0 :territory/region] "the region" "Some Region")
                    ;; addresses are commonly multiline
                    (replace-in [:territories 0 :territory/addresses] "the addresses" "Some Street\nAnother Street\n"))]
      ;; newline is used as the separator, so that you could not accidentally search from two
      ;; adjacent fields at the same time (one does not simply type a newline to a search field)
      (is (str/includes? (str (territory-list-page/view model))
                         "data-searchable=\"123a\nsome region\nsome street\nanother street\""))
      (is (str/includes? (str (territory-list-page/view model))
                         "data-territory-id=\"00000000-0000-0000-0000-000000000001\""))))

  (testing "missing territory number: shows a placeholder so that the link can be clicked"
    (is (= (html/normalize-whitespace
            "Territories

             Search [] Clear
             Number ↑   Region       Addresses       Status         Last covered
             -          the region   the addresses   Up for grabs")
           (-> (territory-list-page/view (replace-in model [:territories 0 :territory/number] "123" ""))
               html/visible-text))))

  (testing "territory-list-map's JSON data is guarded against XSS"
    ;; If we used a <script type="application/json"> element, we would need to
    ;; guard against "</script>" strings. By using a <template> element, we can
    ;; rely on basic HTML encoding, which Hiccup does automatically.
    (is (str/includes? (str (territory-list-page/view (replace-in model [:territories 0 :territory/number] "123" "</script>")))
                       "&quot;number&quot;:&quot;&lt;/script&gt;&quot;")))

  (testing "loans lazy loading:"
    ;; Loan data is currently loaded from Google Sheets, which can easily take a couple of seconds.
    ;; Lazy loading is needed to load the territory list page faster and defer rendering the map.
    (let [map-html "<territory-list-map"
          placeholder-icon "{map-location.svg}"]
      (testing "loans disabled -> show map immediately"
        (let [rendered (territory-list-page/view model)]
          (is (str/includes? rendered map-html))
          (is (not (str/includes? (html/visible-text rendered) placeholder-icon)))))

      (testing "loans enabled -> show a placeholder and lazy load the map"
        (let [rendered (territory-list-page/view model-loans-enabled)]
          (is (not (str/includes? rendered map-html)))
          (is (str/includes? (html/visible-text rendered) placeholder-icon))))))

  (testing "limited visibility disclaimer:"
    (testing "anonymous, has opened a share"
      (is (= (html/normalize-whitespace
              "Territories

               {info.svg} Why so few territories?
               Only those territories which have been shared with you are currently shown.
               You will need to login to see the rest.

               Search [] Clear
               Number ↑   Region       Addresses       Status         Last covered
               123        the region   the addresses   Up for grabs")
             (-> (territory-list-page/view anonymous-model)
                 html/visible-text))))

    (testing "logged in without congregation access, has opened a share"
      (is (= (html/normalize-whitespace
              "Territories

               {info.svg} Why so few territories?
               Only those territories which have been shared with you are currently shown.
               You will need to request access to see the rest.

               Search [] Clear
               Number ↑   Region       Addresses       Status         Last covered
               123        the region   the addresses   Up for grabs")
             (binding [auth/*user* {:user/id (random-uuid)}]
               (-> (territory-list-page/view anonymous-model)
                   html/visible-text)))))))

(deftest route-conflicts-test
  (let [router (reitit/router ui/routes)
        uuid (str (random-uuid))]
    (testing "htmx component URLs shouldn't conflict with territory page URLs"
      (is (= ::territory-list-page/map
             (-> (reitit/match-by-path router (str "/congregation/" uuid "/territories/map"))
                 :data :name)))
      (is (= ::territory-list-page/table
             (-> (reitit/match-by-path router (str "/congregation/" uuid "/territories/table"))
                 :data :name)))
      (is (= ::territory-page/page
             (-> (reitit/match-by-path router (str "/congregation/" uuid "/territories/" uuid))
                 :data :name))))))
