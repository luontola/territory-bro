(ns territory-bro.domain.demo
  (:refer-clojure :exclude [rand-nth random-uuid])
  (:require [clojure.test :refer :all])
  (:import (java.time LocalDate)
           (java.util Random UUID)))

(def cong-id "demo")

(defn- random-uuid [^Random random]
  (UUID. (.nextLong random) (.nextLong random)))

(defn- rand-nth [^Random random coll]
  (nth coll (.nextInt random (count coll))))


(def publishers-by-id
  (let [random (Random. 1)]
    (reduce (fn [m publisher]
              (let [publisher-id (random-uuid random)]
                (assoc m publisher-id (assoc publisher
                                             :congregation/id "demo"
                                             :publisher/id publisher-id))))
            {}
            [{:publisher/name "Andrew"}
             {:publisher/name "Bartholomew"}
             {:publisher/name "James, son of Alphaeus"}
             {:publisher/name "James, son of Zebedee"}
             {:publisher/name "John, son of Zebedee"}
             {:publisher/name "Matthew"}
             {:publisher/name "Matthias"}
             {:publisher/name "Philip"}
             {:publisher/name "Simon Peter"}
             {:publisher/name "Simon, the Cananaean"}
             {:publisher/name "Thaddaeus"}
             {:publisher/name "Thomas"}])))

(def publishers (vals publishers-by-id))


(defn generate-assignment-events [^UUID territory-id ^LocalDate today]
  (let [random (Random. (bit-xor (.getLeastSignificantBits territory-id)
                                 (.getMostSignificantBits territory-id)))]
    (loop [dates (->> (.minusYears today 3)
                      (iterate #(.plusDays ^LocalDate % (.nextInt random 300)))
                      (take-while #(. ^LocalDate % isBefore today))
                      doall)
           assignment-id nil
           events (transient [])]
      (let [[date & dates] dates]
        (cond
          (nil? date)
          (persistent! events)

          (nil? assignment-id)
          (let [assignment-id (random-uuid random)
                publisher-id (:publisher/id (rand-nth random publishers))]
            (recur dates
                   assignment-id
                   (conj! events {:event/type :territory.event/territory-assigned
                                  :congregation/id cong-id
                                  :territory/id territory-id
                                  :assignment/id assignment-id
                                  :assignment/start-date date
                                  :publisher/id publisher-id})))

          :else
          (recur dates
                 nil
                 (-> events
                     (conj! {:event/type :territory.event/territory-covered
                             :congregation/id cong-id
                             :territory/id territory-id
                             :assignment/id assignment-id
                             :assignment/covered-date date})
                     (conj! {:event/type :territory.event/territory-returned
                             :congregation/id cong-id
                             :territory/id territory-id
                             :assignment/id assignment-id
                             :assignment/end-date date}))))))))
