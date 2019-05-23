;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.json
  (:require [jsonista.core :as j])
  (:import (com.fasterxml.jackson.databind ObjectMapper)))

(def ^ObjectMapper mapper
  (j/object-mapper {:decode-key-fn true}))

(defn ^String generate-string [obj]
  (j/write-value-as-string obj mapper))

(defn parse-string [^String json]
  (j/read-value json mapper))
