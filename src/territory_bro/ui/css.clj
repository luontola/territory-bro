;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.css
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [territory-bro.infra.json :as json]
            [territory-bro.infra.resources :as resources]))

(def ^:private *stylesheet-path (atom {:resource (io/resource "public/index.html")}))

(defn stylesheet-path []
  ;; XXX: make auto-refresh work for non-map values
  (:path (resources/auto-refresh *stylesheet-path #(identity {:path (re-find #"/assets/index-\w+\.css" (slurp %))}))))


(def ^:private *css-modules (atom {:resource (io/resource "css-modules.json")}))

(defn modules []
  (resources/auto-refresh *css-modules #(json/read-value (slurp %))))


(defn classes [& names]
  (str/join " " names))
