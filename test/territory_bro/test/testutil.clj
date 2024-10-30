;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.test.testutil
  (:require [clojure.test :refer :all]
            [territory-bro.commands :as commands]
            [territory-bro.domain.dmz :as dmz]
            [territory-bro.events :as events]
            [territory-bro.infra.authentication :as auth]
            [territory-bro.infra.foreign-key :as foreign-key]
            [territory-bro.projections :as projections])
  (:import (java.util UUID)
           (java.util.regex Pattern)))

;; these can be required to avoid IDE warnings about the built-in clojure.test/is macro special forms
(declare ^{:arglists '([exception-class body])}
         thrown?)
(declare ^{:arglists '([exception-class regex body])}
         thrown-with-msg?)

(defn re-equals [^String s]
  (re-pattern (str "^" (Pattern/quote s) "$")))

(defn re-contains [^String s]
  (re-pattern (Pattern/quote s)))

(defn replace-in [m ks old-value new-value]
  (update-in m ks (fn [value]
                    (assert (= old-value value)
                            (str "expected " ks
                                 " to be " (pr-str old-value)
                                 " but it was " (pr-str value)))
                    new-value)))

(defn close-to? [a b]
  (< (abs (- a b))
     0.00001))

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
  {:card-minimap-viewport (constantly true)
   :congregation (constantly true)
   :congregation-boundary (constantly true)
   :new (constantly true)
   :publisher (constantly true)
   :region (constantly true)
   :share (constantly true)
   :territory (constantly true)
   :unsafe (constantly true)
   :user (constantly true)
   :user-or-anonymous (constantly true)})

(defn validate-command [command]
  (binding [foreign-key/*reference-checkers* dummy-reference-checkers]
    (commands/validate-command command)))

(defn validate-commands [commands]
  (doseq [command commands]
    (validate-command command))
  commands)

(defn apply-events
  ([projection events]
   (apply-events projection nil events))
  ([projection state events]
   (reduce projection state (events/validate-events events))))

(defmacro with-events [events & body]
  `(binding [dmz/*state* (apply-events projections/projection dmz/*state* ~events)]
     ~@body))

(defmacro with-request-state [request & body]
  `(binding [dmz/*state* (dmz/enrich-state-for-request dmz/*state* ~request)]
     ~@body))

(defmacro with-user-id [user-id & body]
  `(auth/with-user-id ~user-id
     (with-request-state nil
       ~@body)))

(defmacro with-anonymous-user [& body]
  `(auth/with-anonymous-user
     (with-request-state nil
       ~@body)))

(defmacro with-super-user [& body]
  `(with-user-id (UUID/randomUUID)
     (with-request-state {:session {::dmz/sudo? true}}
       ~@body)))