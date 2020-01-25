;; Copyright © 2015-2020 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.json
  (:require [jsonista.core :as j]
            [schema.core :as s])
  (:import (com.fasterxml.jackson.databind ObjectMapper)))

(def ^ObjectMapper mapper
  (j/object-mapper {:decode-key-fn true}))

(defn ^String generate-string [obj]
  (j/write-value-as-string obj mapper))

(defn parse-string [^String json]
  (j/read-value json mapper))

(s/defschema Schema
  (s/maybe
   (s/conditional
    string? s/Str
    integer? s/Int
    float? (s/constrained Double #(Double/isFinite %)) ; JSON doesn't support Infinite and NaN
    boolean? s/Bool
    map? {s/Keyword (s/recursive #'Schema)}
    coll? [(s/recursive #'Schema)])))
