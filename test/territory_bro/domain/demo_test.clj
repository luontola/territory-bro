(ns territory-bro.domain.demo-test
  (:require [clojure.test :refer :all]
            [territory-bro.domain.demo :as demo]
            [territory-bro.events :as events])
  (:import (java.time LocalDate)
           (java.util UUID)))

(deftest publishers-test
  (is (= 12 (count demo/publishers)))
  (let [publisher {:congregation/id "demo"
                   :publisher/id #uuid "a8eeba81-0ad4-107c-2820-9046c2fd1ce6"
                   :publisher/name "Simon Peter"}]
    (is (= publisher (get demo/publishers-by-id (:publisher/id publisher))))
    (is (contains? (set demo/publishers) publisher))))

(deftest generate-assignment-events-test
  (let [territory-id (UUID. 0 1)
        today (LocalDate/of 2010 1 1)
        events (demo/generate-assignment-events territory-id today)]

    (testing "generates valid events, except that cong-id is 'demo'"
      (is (= ["demo"] (distinct (map :congregation/id events))))
      (is (->> events
               (map #(assoc % :congregation/id (random-uuid)))
               events/validate-events)))

    (testing "generates the same events for the same territory"
      (is (= events (demo/generate-assignment-events territory-id today))))

    (testing "generates different events for other territories"
      (is (not= events (demo/generate-assignment-events (UUID. 0 2) today))))))
