;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.territory-page
  (:require [hiccup2.core :as h]
            [territory-bro.ui.layout :as layout]))

(defn page [request]
  (layout/page {:title "Territory Page"}
    (h/html
     [:h1 "Territory Page"]
     [:p "Hello from server"])))
