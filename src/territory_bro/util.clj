;; Copyright © 2015-2018 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.util
  (:import (java.sql SQLException)))

(defn fix-sqlexception-chain [e]
  (when (instance? SQLException e)
    (if-let [next (.getNextException e)]
      (if (.getCause e)
        (.addSuppressed e next)
        (.initCause e next))))
  ; XXX: Will recurse through getNextException chain because it is returned by getCause.
  ; FIXME: Will not recurse through getNextException chain if there already was a cause and the chain was suppressed
  (when (instance? Throwable e)
    (fix-sqlexception-chain (.getCause e)))
  e)

(defn getx [map key]
  (let [value (get map key)]
    (if (nil? value)
      (throw (IllegalArgumentException. (str "key " key " is missing")))
      value)))
