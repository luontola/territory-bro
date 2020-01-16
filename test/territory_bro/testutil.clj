;; Copyright © 2015-2020 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.testutil
  (:require [clojure.test :refer :all]
            [territory-bro.commands :as commands]
            [territory-bro.events :as events]
            [territory-bro.foreign-key :as foreign-key])
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

(def dummy-reference-checkers
  {:congregation (constantly true)
   :new (constantly true)
   :user (constantly true)})

(defn validate-command [command]
  (binding [foreign-key/*reference-checkers* dummy-reference-checkers]
    (commands/validate-command command)))

(defn validate-commands [commands]
  (doseq [command commands]
    (validate-command command))
  commands)

(defn apply-events [projection events]
  (reduce projection nil (events/validate-events events)))
