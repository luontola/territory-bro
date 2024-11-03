(ns territory-bro.infra.json
  (:require [jsonista.core :as json]
            [schema.core :as s])
  (:import (com.fasterxml.jackson.databind ObjectMapper)))

(def ^:private ^ObjectMapper default-mapper
  (json/object-mapper {:decode-key-fn true}))

(defn ^String write-value-as-string [obj]
  (json/write-value-as-string obj default-mapper))

(defn read-value [^String json]
  (json/read-value json default-mapper))

(s/defschema Schema
  (s/maybe
   (s/conditional
    string? s/Str
    integer? s/Int
    float? (s/constrained Double Double/isFinite) ; JSON doesn't support Infinite and NaN
    boolean? s/Bool
    map? {s/Keyword (s/recursive #'Schema)}
    coll? [(s/recursive #'Schema)])))
