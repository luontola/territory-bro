(ns territory-bro.domain.conversion-funnel-test
  (:require [clojure.math :as math]
            [clojure.string :as str]
            [clojure.test :refer :all]
            [territory-bro.domain.congregation :as congregation]
            [territory-bro.domain.conversion-funnel :as conversion-funnel]
            [territory-bro.domain.demo :as demo]
            [territory-bro.domain.dmz-test :as dmz-test]
            [territory-bro.domain.share :as share]
            [territory-bro.domain.territory :as territory]
            [territory-bro.projections :as projections]
            [territory-bro.test.testutil :as testutil]
            [territory-bro.ui.hiccup :as h])
  (:import (java.time Instant LocalDateTime OffsetDateTime YearMonth ZoneOffset)
           (java.time.temporal ChronoUnit)))

(def cong-id dmz-test/cong-id)
(def t1 (Instant/ofEpochSecond 1))
(def t2 (Instant/ofEpochSecond 2))

(defn again [event]
  (assoc event :event/time (Instant/now)))

(defn- apply-events [events]
  (-> (testutil/apply-events (fn [state event]
                               (-> state
                                   (territory/projection event)
                                   (share/projection event)
                                   (conversion-funnel/projection event)))
                             events)
      (select-keys [::conversion-funnel/milestones])))

(deftest projection-test
  (testing "tracks when congregation was created"
    (let [event (assoc dmz-test/congregation-created :event/time t1)
          expected {::conversion-funnel/milestones
                    {cong-id {:congregation-created t1}}}]
      (is (= expected (apply-events [event])))
      (is (= expected (apply-events [event (again event)]))
          "records only the first time it happens")))

  (testing "tracks when congregation boundary was created"
    (let [event (assoc dmz-test/congregation-boundary-defined :event/time t1)
          expected {cong-id {:congregation-boundary-created t1}}]
      (is (= expected (::conversion-funnel/milestones (apply-events [event]))))
      (is (= expected (::conversion-funnel/milestones (apply-events [event (again event)])))
          "records only the first time it happens")))

  (testing "tracks when region was created"
    (let [event (assoc dmz-test/region-defined :event/time t1)
          expected {::conversion-funnel/milestones
                    {cong-id {:region-created t1}}}]
      (is (= expected (apply-events [event])))
      (is (= expected (apply-events [event (again event)]))
          "records only the first time it happens")))

  (testing "tracks when territory was created"
    (let [event (assoc dmz-test/territory-defined :event/time t1)
          expected {::conversion-funnel/milestones
                    {cong-id {:territory-created t1}}}]
      (is (= expected (apply-events [event])))
      (is (= expected (apply-events [event (again event)]))
          "records only the first time it happens")))

  (testing "tracks when 10 territories were created"
    (let [events-1-9 (for [t (range 1 10)]
                       (assoc dmz-test/territory-defined
                              :territory/id (random-uuid)
                              :event/time (Instant/ofEpochSecond t)))
          event-10 (assoc dmz-test/territory-defined
                          :territory/id (random-uuid)
                          :event/time (Instant/ofEpochSecond 10))
          event-11 (assoc dmz-test/territory-defined
                          :territory/id (random-uuid)
                          :event/time (Instant/ofEpochSecond 11))
          expected-before {::conversion-funnel/milestones
                           {cong-id {:territory-created t1}}}
          expected-after {::conversion-funnel/milestones
                          {cong-id {:territory-created t1
                                    :ten-territories-created (Instant/ofEpochSecond 10)}}}]
      (assert (= 9 (count events-1-9)))
      (is (= expected-before (apply-events events-1-9))
          "before the 10th territory")
      (is (= expected-after (apply-events (concat events-1-9 [event-10]))))
      (is (= expected-after (apply-events (concat events-1-9 [event-10 event-11])))
          "records only the first time it happens")))

  (testing "tracks when territory was assigned"
    (let [event (assoc dmz-test/territory-assigned :event/time t1)
          expected {::conversion-funnel/milestones
                    {cong-id {:territory-assigned t1}}}]
      (is (= expected (apply-events [event])))
      (is (= expected (apply-events [event (again event)]))
          "records only the first time it happens")))

  (testing "tracks when a shared link was created"
    (let [event (assoc dmz-test/share-created
                       :event/time t1
                       :share/type :link)
          expected {::conversion-funnel/milestones
                    {cong-id {:share-link-created t1}}}]
      (is (= expected (apply-events [event])))
      (is (= expected (apply-events [event (again event)]))
          "records only the first time it happens")))

  (testing "doesn't track when a shared link was opened"
    (let [created-event (assoc dmz-test/share-created
                               :event/time t1
                               :share/type :link)
          opened-event {:event/type :share.event/share-opened
                        :event/time t2
                        :share/id dmz-test/share-id}
          expected {::conversion-funnel/milestones
                    {cong-id {:share-link-created t1}}}]
      (is (= expected (apply-events [created-event]))
          "before opening")
      (is (= expected (apply-events [created-event opened-event]))
          "after opening")))

  (testing "doesn't track when a QR code was created"
    (let [event (assoc dmz-test/share-created
                       :event/time t1
                       :share/type :qr-code)
          expected {}]
      (is (= expected (apply-events [event])))))

  (testing "tracks when a QR code was scanned"
    (let [created-event (assoc dmz-test/share-created
                               :event/time t1
                               :share/type :qr-code)
          opened-event {:event/type :share.event/share-opened
                        :event/time t2
                        :share/id dmz-test/share-id}
          expected {::conversion-funnel/milestones
                    {cong-id {:qr-code-scanned t2}}}]
      (is (= expected (apply-events [created-event opened-event])))
      (is (= expected (apply-events [created-event opened-event (again opened-event)]))
          "records only the first time it happens"))))


;;;; Reporting

(defn quarter [month]
  (inc (long (/ (dec month) 3))))

(deftest quarter-test
  (is (= 1 (quarter 1) (quarter 2) (quarter 3)))
  (is (= 2 (quarter 4) (quarter 5) (quarter 6)))
  (is (= 3 (quarter 7) (quarter 8) (quarter 9)))
  (is (= 4 (quarter 10) (quarter 11) (quarter 12))))

(defn year-quarter [^OffsetDateTime timestamp]
  (let [ym (YearMonth/from timestamp)]
    (str (.getYear ym) "-Q" (quarter (.getMonthValue ym)))))

(defn count-milestone [milestones k]
  (count (filterv some? (mapv k milestones))))

(defn render-report [{:keys [milestones-by-year show-congregations?]}]
  (let [milestone-keys [:congregation-created
                        #_:congregation-boundary-created
                        #_:region-created
                        :territory-created
                        :ten-territories-created
                        #_:territory-assigned
                        :share-link-created
                        :qr-code-scanned]]
    (str (h/html
          [:html
           [:head
            [:title "Conversion Funnel"]
            [:link {:rel "stylesheet"
                    :href "https://cdn.jsdelivr.net/npm/purecss@3.0.0/build/pure-min.css"
                    :integrity "sha384-X38yfunGUhNzHpBaEBsWLO+A0HDYOQi8ufWDkZ0k9e0eXz/tH3II7uKZ9msv++Ls"
                    :crossorigin "anonymous"}]]
           [:body
            [:table.pure-table
             [:thead {:style {:position "sticky"
                              :top "-1px"}} ; the header has a top border which doesn't stick, but leaves a 1px see-through gap
              [:tr
               [:th "Year"]
               (for [k milestone-keys]
                 [:th (str/replace (name k) "-" " ")])]]
             [:tbody
              (for [[year milestones] (sort-by first milestones-by-year)]
                (let [total (count-milestone milestones :congregation-created)]
                  (list
                   [:tr (when show-congregations?
                          {:style {:border-top "1px solid #cbcbcb"}})
                    [:td year]
                    (for [k milestone-keys]
                      (let [n (count-milestone milestones k)
                            percent (math/round (* 100 (/ n total)))]
                        [:td percent "% (" n ")"]))]
                   (when show-congregations?
                     (for [cong (sort-by :congregation/name milestones)]
                       [:tr
                        [:td]
                        [:td (:congregation/name cong)]
                        (for [k (drop 1 milestone-keys)]
                          [:td (if (pos? (count-milestone [cong] k))
                                 "✅")])])))))]]]]))))

(defn write-file! [file content]
  (spit file content)
  (println (-> (LocalDateTime/now) (.truncatedTo ChronoUnit/SECONDS) str)
           "- Wrote" file))

(defn build-report []
  (let [state (projections/cached-state)
        milestones-by-cong-id (-> (::conversion-funnel/milestones state)
                                  (dissoc demo/cong-id))
        milestones (->> milestones-by-cong-id
                        (mapv (fn [[cong-id milestones]]
                                (assoc milestones
                                       :congregation/id cong-id
                                       :congregation/name (:congregation/name (congregation/get-unrestricted-congregation state cong-id)))))
                        (remove #(str/starts-with? (:congregation/name %) "Test Congregation")))
        milestones-by-year (->> milestones
                                (group-by (fn [milestones]
                                            (let [^Instant created (:congregation-created milestones)]
                                              (year-quarter (.atOffset created ZoneOffset/UTC))))))]
    (write-file! "target/conversion-funnel.html"
                 (render-report {:milestones-by-year milestones-by-year
                                 :show-congregations? false}))
    (write-file! "target/conversion-funnel-details.html"
                 (render-report {:milestones-by-year milestones-by-year
                                 :show-congregations? true}))))

(comment
  (build-report))
