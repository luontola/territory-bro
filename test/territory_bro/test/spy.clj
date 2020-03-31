;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.test.spy
  (:refer-clojure :exclude [fn])
  (:require [clojure.test :refer :all]))

(defn fn
  ([spy name]
   (fn spy name nil))
  ([spy name listener]
   (clojure.core/fn [& args]
     (when listener
       (apply listener args))
     (swap! spy conj (vec (cons name args))))))

(defn read! [spy]
  (let [results @spy]
    (reset! spy [])
    results))

(defn spy []
  (atom []))
