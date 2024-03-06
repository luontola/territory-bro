;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.info-box
  (:require [hiccup2.core :as h]
            [territory-bro.ui.css :as css]))

(defn view [{:keys [title]} content]
  (let [styles (:InfoBox (css/modules))]
    (h/html
     [:div {:class (:root styles)}
      (when (some? title)
        [:div {:class (:title styles)}
         [:i.fa-solid.fa-info-circle]
         " "
         title])
      [:div {:class (:content styles)}
       content]])))
