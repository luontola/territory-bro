(ns territory-bro.ui.map-interaction-help
  (:require [clojure.string :as str]
            [territory-bro.ui.hiccup :as h]
            [territory-bro.ui.i18n :as i18n]
            [territory-bro.ui.info-box :as info-box]))

(defn model [request]
  (let [user-agent (str (get-in request [:headers "user-agent"]))]
    {:mac? (str/includes? user-agent "Mac OS")}))

(defn view [{:keys [mac?]}]
  (let [ctrl (if mac? "⌘ Command" "Ctrl")
        alt (if mac? "⌥ Option" "Alt")
        shift (if mac? "⇧ Shift" "Shift")]
    (info-box/view
     {:title (i18n/t "MapInteractionHelp.title")}
     (h/html
      [:p {} (h/raw (i18n/t "MapInteractionHelp.move"))]
      [:p {} (h/raw (-> (i18n/t "MapInteractionHelp.zoom")
                        (str/replace "{{ctrl}}" ctrl)))]
      [:p {} (h/raw (-> (i18n/t "MapInteractionHelp.rotate")
                        (str/replace "{{alt}}" alt)
                        (str/replace "{{shift}}" shift)))]))))
