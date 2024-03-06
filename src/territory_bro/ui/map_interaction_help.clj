;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.map-interaction-help
  (:require [clojure.string :as str]
            [hiccup2.core :as h]
            [territory-bro.ui.i18n :as i18n]
            [territory-bro.ui.info-box :as info-box]))

(defn model [request]
  (let [user-agent (str (get-in request [:headers "user-agent"]))]
    {:mac? (str/includes? user-agent "Mac OS")}))

(defn view [{:keys [mac?]}]
  (info-box/view
   {:title (i18n/t "MapInteractionHelp.title")}
   (h/html
    [:p (h/raw (i18n/t "MapInteractionHelp.move"))]
    [:p (h/raw (-> (i18n/t "MapInteractionHelp.zoom")
                   (str/replace "{{ctrl}}" (if mac? "Cmd" "Ctrl"))))]
    [:p (h/raw (-> (i18n/t "MapInteractionHelp.rotate")
                   (str/replace "{{alt}}" (if mac? "Option" "Alt"))))])))
