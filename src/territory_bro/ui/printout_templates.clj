;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.printout-templates
  (:require [hiccup2.core :as h]
            [territory-bro.ui.css :as css]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.i18n :as i18n])
  (:import (java.time LocalDate ZoneId ZoneOffset)
           (net.iakovlev.timeshape TimeZoneEngine)
           (org.locationtech.jts.io WKTReader)))

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


(defonce *timezone-engine (future (TimeZoneEngine/initialize))) ; takes 370ms on an Apple M3 Max, so worth initializing on the background

(defn timezone-for-location ^ZoneId [^String location]
  (time (let [point (-> (WKTReader.)
                        (.read location)
                        (.getInteriorPoint))]
          (-> (.query @*timezone-engine (.getY point) (.getX point))
              (.orElse ZoneOffset/UTC)))))

(defn print-date-notice [^LocalDate print-date content]
  (let [styles (:PrintDateNotice (css/modules))]
    (h/html
     [:div {:class (:root styles)}
      [:div {:class (:notice styles)} (str "Printed " print-date " with TerritoryBro.com")]
      [:div {:class (:content styles)}
       content]])))


(defn territory-card [{:keys [territory map-raster]}]
  (let [styles (:TerritoryCard (css/modules))]
    (crop-marks
     (h/html
      [:div {:class (:root styles)}

       [:div {:class (:minimap styles)}
        ;; TODO: map using web components
        [:div.OpenLayersMap__root--f9d8701d.OpenLayersMap__printout--f9d8701d
         [:div.ol-viewport {:style "position: relative; overflow: hidden; width: 100%; height: 100%;"}
          "[OpenLayersMap]"]]]

       [:div {:class (:header styles)}
        [:div {:class (:title styles)} (i18n/t "TerritoryCard.title")]
        [:div {:class (:region styles)} (:region territory)]]

       [:div {:class (:number styles)} (:number territory)]

       [:div {:class (:map styles)}
        (print-date-notice
         (LocalDate/now (timezone-for-location (:location territory)))
         [:territory-map {:location (:location territory)
                          :map-raster map-raster
                          :printout true}])]

       [:div {:class (:addresses styles)}
        [:div {:class (:qrCode styles)}
         ;; TODO: QR code using lazy loading with caching
         [:svg {:height "128" :width "128" :viewBox "0 0 29 29" :style "width: 100%; height: auto;"}
          [:path {:fill "#FFFFFF" :d "M0,0 h29v29H0z" :shape-rendering "crispEdges"}]
          [:path {:fill "#000000" :d "M0 0h7v1H0zM11 0h1v1H11zM14 0h1v1H14zM18 0h1v1H18zM22,0 h7v1H22zM0 1h1v1H0zM6 1h1v1H6zM8 1h2v1H8zM13 1h1v1H13zM17 1h1v1H17zM22 1h1v1H22zM28,27 h1v1H28zM0 28h7v1H0zM8 28h1v1H8zM10 28h3v1H10zM14 28h2v1H14zM19 28h1v1H19zM24 28h3v1H24z" :shape-rendering "crispEdges"}]]]
        (:addresses territory)]

       [:div {:class (:footer styles)} (i18n/t "TerritoryCard.footer")]]))))
