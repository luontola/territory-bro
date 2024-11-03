(ns territory-bro.infra.util
  (:import (java.sql SQLException)
           (java.util Base64)
           (net.greypanther.natsort CaseInsensitiveSimpleNaturalComparator)))

(defn fix-sqlexception-chain [^Throwable e]
  (when (instance? SQLException e)
    (if-let [next (.getNextException ^SQLException e)]
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

(def conj-set (fnil conj #{}))

(defn assoc-dissoc [m k v]
  (if (some? v)
    (assoc m k v)
    (dissoc m k)))

(defn decode-base64url [^String base64-str]
  (-> (Base64/getUrlDecoder)
      (.decode base64-str)
      (String.)))

(defn natural-sort-by [keyfn coll]
  (sort-by (comp str keyfn)
           (CaseInsensitiveSimpleNaturalComparator/getInstance)
           coll))
