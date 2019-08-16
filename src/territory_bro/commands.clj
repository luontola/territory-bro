;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.commands
  (:require [schema-refined.core :as refined]
            [schema.core :as s]
            [schema.utils])
  (:import (java.time Instant)
           (java.util UUID)))

(s/defschema CommandBase
  {:command/type s/Keyword
   :command/time Instant
   :command/user UUID})

(s/defschema RenameCongregation
  (assoc CommandBase
         :command/type (s/eq :congregation.command/rename-congregation)
         :congregation/id UUID
         :congregation/name s/Str))


(def command-schemas
  {:congregation.command/rename-congregation RenameCongregation})

(s/defschema Command
  (apply refined/dispatch-on :command/type (flatten (seq command-schemas))))


;;;; Validation

(defn validate-command [command]
  (when-not (contains? command-schemas (:command/type command))
    (throw (ex-info (str "Unknown command type " (pr-str (:command/type command)))
                    {:command command})))
  (assert (contains? command-schemas (:command/type command))
          {:error [:unknown-command-type (:command/type command)]
           :command command})
  (s/validate Command command))
