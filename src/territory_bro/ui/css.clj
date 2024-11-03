(ns territory-bro.ui.css
  (:require [territory-bro.infra.json :as json]
            [territory-bro.infra.resources :as resources]))

(def modules
  (resources/auto-refresher "css-modules.json" #(json/read-value (slurp %))))
