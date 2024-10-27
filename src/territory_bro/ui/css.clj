;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.css
  (:require [territory-bro.infra.json :as json]
            [territory-bro.infra.resources :as resources]))

(def modules
  (resources/auto-refresher "css-modules.json" #(json/read-value (slurp %))))
