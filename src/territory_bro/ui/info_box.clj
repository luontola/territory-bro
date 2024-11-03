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
