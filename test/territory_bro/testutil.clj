;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.testutil
  (:require [clojure.test :refer :all])
  (:import (java.util.regex Pattern)))

(defn re-equals [^String s]
  (re-pattern (str "^" (Pattern/quote s) "$")))

(defn re-contains [^String s]
  (re-pattern (Pattern/quote s)))

(defmacro grab-exception [& body]
  `(try
     (let [result# (do ~@body)]
       (do-report {:type :fail
                   :message "should have thrown an exception, but did not"
                   :expected (seq (into [(symbol "grab-exception")]
                                        '~body))
                   :actual result#})
       result#)
     (catch Throwable t#
       t#)))
