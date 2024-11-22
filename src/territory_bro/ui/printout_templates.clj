(ns territory-bro.ui.printout-templates
  (:require [territory-bro.ui.css :as css]
            [territory-bro.ui.hiccup :as h]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.i18n :as i18n])
  (:import (java.time LocalDate)))

(defn crop-marks [content]
  (let [styles (:CropMarks (css/modules))
        image (h/html [:img {:src (get html/public-resources "/assets/crop-mark.*.svg")
                             :alt ""}])]
    (h/html
     [:div {:class (:root styles)}
      [:div {:class (:topLeft styles)} image]
      [:div {:class (:topRight styles)} image]
      [:div {:class (:cropArea styles)} content]
      [:div {:class (:bottomLeft styles)} image]
      [:div {:class (:bottomRight styles)} image]])))

(defn a5-print-frame [content]
  ;; TODO: deduplicate print frames
  (let [styles (:A5PrintFrame (css/modules))]
    (h/html
     [:div {:class (:cropArea styles)}
      content])))

(defn a4-print-frame [content]
  ;; TODO: deduplicate print frames
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

(defn territory-qr-code [territory]
  (h/html
   [:div {:hx-target "this"
          :hx-swap "outerHTML"
          :hx-trigger "load"
          :hx-get (str html/*page-path* "/qr-code/" (:territory/id territory))}]))


(defn territory-card [{:keys [territory congregation-boundary enclosing-region enclosing-minimap-viewport map-raster print-date qr-codes-allowed?]}]
  (let [styles (:TerritoryCard (css/modules))]
    (crop-marks
     (h/html
      [:div {:class (:root styles)}

       [:div {:class (:minimap styles)}
        [:territory-mini-map {:territory-location (:territory/location territory)
                              :congregation-boundary congregation-boundary
                              :enclosing-region enclosing-region
                              :enclosing-minimap-viewport enclosing-minimap-viewport
                              :map-raster map-raster}]]

       [:div {:class (:header styles)}
        [:div {:class (:title styles)}
         (i18n/t "TerritoryCard.title")]
        [:div {:class (:region styles)}
         (:territory/region territory)]]

       [:div {:class (:number styles)}
        (:territory/number territory)]

       [:div {:class (:map styles)}
        (print-date-notice
         print-date
         [:territory-map {:territory-location (:territory/location territory)
                          :settings-key (str "printout/territory-card/" (:territory/id territory))
                          :map-raster map-raster
                          :printout true}])]

       [:div {:class (:addresses styles)}
        (when qr-codes-allowed?
          [:div {:class (:qrCode styles)}
           (territory-qr-code territory)])
        (:territory/addresses territory)]

       [:div {:class (:footer styles)}
        (i18n/t "TerritoryCard.footer")]]))))

(defn territory-card-map-only [{:keys [territory congregation-boundary enclosing-region enclosing-minimap-viewport map-raster print-date qr-codes-allowed?]}]
  ;; TODO: deduplicate with TerritoryCard
  (let [styles (:TerritoryCardMapOnly (css/modules))]
    (crop-marks
     (h/html
      [:div {:class (:root styles)}

       [:div {:class (:minimap styles)}
        [:territory-mini-map {:territory-location (:territory/location territory)
                              :congregation-boundary congregation-boundary
                              :enclosing-region enclosing-region
                              :enclosing-minimap-viewport enclosing-minimap-viewport
                              :map-raster map-raster}]]

       [:div {:class (:header styles)}
        [:div {:class (:title styles)}
         (i18n/t "TerritoryCard.title")]
        [:div {:class (:region styles)}
         (:territory/region territory)]]

       [:div {:class (:number styles)}
        (:territory/number territory)]

       [:div {:class (:map styles)}
        (print-date-notice
         print-date
         [:territory-map {:territory-location (:territory/location territory)
                          :settings-key (str "printout/territory-card-map-only/" (:territory/id territory))
                          :map-raster map-raster
                          :printout true}])]

       (when qr-codes-allowed?
         [:div {:class (:qrCode styles)}
          (territory-qr-code territory)])

       [:div {:class (:footer styles)}
        (i18n/t "TerritoryCard.footer")]]))))

(defn rural-territory-card [{:keys [territory congregation-boundary enclosing-region enclosing-minimap-viewport map-raster print-date qr-codes-allowed?]}]
  ;; TODO: deduplicate with TerritoryCard
  (let [styles (:RuralTerritoryCard (css/modules))]
    (a5-print-frame
     (h/html
      [:div {:class (:root styles)}

       [:div {:class (:minimap styles)}
        [:territory-mini-map {:territory-location (:territory/location territory)
                              :congregation-boundary congregation-boundary
                              :enclosing-region enclosing-region
                              :enclosing-minimap-viewport enclosing-minimap-viewport
                              :map-raster map-raster}]]

       [:div {:class (:header styles)}
        [:div {:class (:title styles)}
         (i18n/t "TerritoryCard.title")]
        [:div {:class (:region styles)}
         (:territory/region territory)]]

       [:div {:class (:number styles)}
        (:territory/number territory)]

       [:div {:class (:map styles)}
        (print-date-notice
         print-date
         [:territory-map {:territory-location (:territory/location territory)
                          :settings-key (str "printout/rural-territory-card/" (:territory/id territory))
                          :map-raster map-raster
                          :printout true}])]

       (when qr-codes-allowed?
         [:div {:class (:qrCode styles)}
          (territory-qr-code territory)])]))))

(defn qr-code-only [{:keys [territory qr-codes-allowed?]}]
  (let [styles (:QrCodeOnly (css/modules))]
    (h/html
     [:div {:class (:cropArea styles)}
      [:div {:class (:root styles)}
       [:div {:class (:number styles)}
        (:territory/number territory)]
       (if qr-codes-allowed?
         [:div {:class (:qrCode styles)}
          (territory-qr-code territory)]
         "QR codes not allowed")]])))

(defn neighborhood-card [{:keys [territory map-raster]}]
  (let [styles (:NeighborhoodCard (css/modules))]
    (crop-marks
     (h/html
      [:div {:class (:root styles)}
       [:neighborhood-map {:territory-number (:territory/number territory)
                           :territory-location (:territory/location territory)
                           :settings-key (str "printout/neighborhood-card/" (:territory/id territory))
                           :map-raster map-raster
                           :printout true}]]))))

(defn region-printout [{:keys [region territories map-raster print-date]}]
  (let [styles (:RegionPrintout (css/modules))]
    (a4-print-frame
     (h/html
      [:div {:class (:root styles)}
       [:div {:class (:name styles)}
        (:region/name region)]
       [:div {:class (:map styles)}
        (print-date-notice
         print-date
         [:region-map {:region-location (:region/location region)
                       :territories territories
                       :settings-key (str "printout/region-printout/" (:region/id region))
                       :map-raster map-raster
                       :printout true}])]]))))
