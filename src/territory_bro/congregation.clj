; Copyright © 2015-2018 Esko Luontola
; This software is released under the Apache License 2.0.
; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.congregation
  (:require [territory-bro.config :refer [env]]))

(defn my-congregations []
  ; TODO: authorization
  (->> (keys (:tenant env))
       (map (fn [id] {:id id
                      ; TODO: human-readable name
                      :name (.toUpperCase (name id))}))))
