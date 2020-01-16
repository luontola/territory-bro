;; Copyright © 2015-2020 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.foreign-key
  (:require [schema.core :as s]
            [schema.spec.variant :as variant]
            [schema.spec.core :as spec]))

(def ^:dynamic *reference-checkers* {})

(s/set-max-value-length! 36) ; long enough to print a UUID in spec/precondition

(defrecord References [entity-type id-schema]
  s/Schema
  (spec [this]
    (let [postcondition (get *reference-checkers* entity-type)]
      (when (nil? postcondition)
        (throw (IllegalStateException. (str "No reference checker for " entity-type
                                            " in " 'territory-bro.foreign-key/*reference-checkers*))))
      (variant/variant-spec
       spec/+no-precondition+
       [{:schema id-schema}]
       nil
       (spec/precondition this postcondition #(list 'foreign-key/references entity-type %)))))
  (explain [_this]
    (list 'foreign-key/references entity-type (s/explain id-schema))))

(defn references [entity-type id-schema]
  (References. entity-type id-schema))
