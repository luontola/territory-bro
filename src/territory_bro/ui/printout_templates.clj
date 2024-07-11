;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.printout-templates
  (:require [hiccup2.core :as h]
            [territory-bro.ui.css :as css]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.i18n :as i18n])
  (:import (java.time LocalDate)))

(defn crop-marks [content]
  (let [styles (:CropMarks (css/modules))
        image (h/html [:img {:src (get html/public-resources "/assets/crop-mark-*.svg")
                             :alt ""}])]
    (h/html
     [:div {:class (:root styles)}
      [:div {:class (:topLeft styles)} image]
      [:div {:class (:topRight styles)} image]
      [:div {:class (:cropArea styles)} content]
      [:div {:class (:bottomLeft styles)} image]
      [:div {:class (:bottomRight styles)} image]])))

(defn a4-print-frame [content]
  (let [styles (:A4PrintFrame (css/modules))]
    (h/html
     [:div {:class (:cropArea styles)}
      content])))

(defn print-date-notice [^LocalDate print-date content]
  (let [styles (:PrintDateNotice (css/modules))]
    (h/html
     [:div {:class (:root styles)}
      [:div {:class (:notice styles)} (str "Printed " print-date " with TerritoryBro.com")]
      [:div {:class (:content styles)}
       content]])))


(defn territory-card [{:keys [territory congregation-boundary enclosing-region enclosing-minimap-viewport map-raster print-date]}]
  (let [styles (:TerritoryCard (css/modules))]
    (crop-marks
     (h/html
      [:div {:class (:root styles)}

       [:div {:class (:minimap styles)}
        [:territory-mini-map {:territory (:location territory)
                              :congregation-boundary congregation-boundary
                              :enclosing-region enclosing-region
                              :enclosing-minimap-viewport enclosing-minimap-viewport}]]

       [:div {:class (:header styles)}
        [:div {:class (:title styles)} (i18n/t "TerritoryCard.title")]
        [:div {:class (:region styles)} (:region territory)]]

       [:div {:class (:number styles)} (:number territory)]

       [:div {:class (:map styles)}
        (print-date-notice
         print-date
         [:territory-map {:location (:location territory)
                          :map-raster map-raster
                          :printout true}])]

       [:div {:class (:addresses styles)}
        [:div {:class (:qrCode styles)}
         [:div {:hx-target "this"
                :hx-swap "outerHTML"
                :hx-trigger "load"
                :hx-get (str html/*page-path* "/qr-code/" (:id territory))}]]
        (:addresses territory)]

       [:div {:class (:footer styles)} (i18n/t "TerritoryCard.footer")]]))))

(defn territory-card-map-only [{:keys [territory congregation-boundary enclosing-region enclosing-minimap-viewport map-raster print-date]}]
  ;; TODO: deduplicate with TerritoryCard
  (let [styles (:TerritoryCardMapOnly (css/modules))]
    (crop-marks
     (h/html
      [:div {:class (:root styles)}

       [:div {:class (:minimap styles)}
        [:territory-mini-map {:territory (:location territory)
                              :congregation-boundary congregation-boundary
                              :enclosing-region enclosing-region
                              :enclosing-minimap-viewport enclosing-minimap-viewport}]]

       [:div {:class (:header styles)}
        [:div {:class (:title styles)} (i18n/t "TerritoryCard.title")]
        [:div {:class (:region styles)} (:region territory)]]

       [:div {:class (:number styles)} (:number territory)]

       [:div {:class (:map styles)}
        (print-date-notice
         print-date
         [:territory-map {:territory (:location territory)
                          :map-raster map-raster
                          :printout true}])]

       [:div {:class (:qrCode styles)}
        [:div {:hx-target "this"
               :hx-swap "outerHTML"
               :hx-trigger "load"
               :hx-get (str html/*page-path* "/qr-code/" (:id territory))}]]

       [:div {:class (:footer styles)} (i18n/t "TerritoryCard.footer")]]))))

(defn region-printout [{:keys [region territories map-raster print-date]}]
  (let [styles (:RegionPrintout (css/modules))]
    (a4-print-frame
     (h/html
      [:div {:class (:root styles)}
       [:div {:class (:name styles)} (:name region)]
       [:div {:class (:map styles)}
        (print-date-notice
         print-date
         [:region-map {:region (:location region)
                       :territories territories
                       :map-raster map-raster
                       :printout true}])]]))))
