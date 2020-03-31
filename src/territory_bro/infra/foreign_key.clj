;; Copyright © 2015-2020 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.infra.foreign-key
  (:require [schema.core :as s]
            [schema.macros :as macros]
            [schema.spec.core :as spec]
            [schema.spec.variant :as variant]))

(def ^:dynamic *reference-checkers* {})

(defrecord References [entity-type id-schema]
  s/Schema
  (spec [this]
    (variant/variant-spec
     spec/+no-precondition+
     [{:schema id-schema}]
     nil
     (fn postcondition [id]
       (let [checker (get *reference-checkers* entity-type)]
         (when (nil? checker)
           (throw (IllegalStateException. (str "No reference checker for " entity-type
                                               " in " 'territory-bro.infra.foreign-key/*reference-checkers*))))
         (when-not (true? (checker id))
           (macros/validation-error this id (list 'foreign-key/references entity-type id) 'violated))))))
  (explain [_this]
    (list 'foreign-key/references entity-type (s/explain id-schema))))

(defn references [entity-type id-schema]
  (References. entity-type id-schema))
