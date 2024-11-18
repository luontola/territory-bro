(ns territory-bro.domain.conversion-funnel-test
  (:require [clojure.test :refer :all]
            [territory-bro.domain.conversion-funnel :as conversion-funnel]
            [territory-bro.domain.dmz-test :as dmz-test]
            [territory-bro.domain.share :as share]
            [territory-bro.domain.territory :as territory]
            [territory-bro.test.testutil :as testutil])
  (:import (java.time Instant)))

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
