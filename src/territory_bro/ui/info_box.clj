;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.info-box
  (:require [territory-bro.ui.css :as css]
            [territory-bro.ui.hiccup :as h]
            [territory-bro.ui.html :as html]))

(defn view [{:keys [title]} content]
  (let [styles (:InfoBox (css/modules))]
    (h/html
     [:div {:class (:root styles)}
      (when (some? title)
        [:div {:class (:title styles)}
         (html/inline-svg "icons/info.svg")
         " "
         title])
      [:div {:class (:content styles)}
       content]])))
