;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.printouts-page
  (:require [clojure.string :as str]
            [hiccup2.core :as h]
            [territory-bro.api :as api]
            [territory-bro.infra.json :as json]
            [territory-bro.infra.resources :as resources]
            [territory-bro.ui.css :as css]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.i18n :as i18n]
            [territory-bro.ui.layout :as layout]
            [territory-bro.ui.map-interaction-help :as map-interaction-help])
  (:import (net.greypanther.natsort CaseInsensitiveSimpleNaturalComparator)))

(def templates
  [{:id "TerritoryCard"
    :type :territory}
   {:id "TerritoryCardMapOnly"
    :type :territory}
   {:id "NeighborhoodCard"
    :type :territory}
   {:id "RuralTerritoryCard"
    :type :territory}
   {:id "Finland2024TerritoryCard"
    :type :territory}
   {:id "QrCodeOnly"
    :type :territory}
   {:id "RegionPrintout"
    :type :region}])

(def map-rasters
  (resources/auto-refresher "map-rasters.json" #(json/read-value (slurp %))))

(defn parse-uuid-multiselect [value]
  ;; Ring form params for a <select> element may be a string or a vector of strings,
  ;; depending on whether one or many values were selected. Coerce them all to a set of UUIDs.
  (if (string? value)
    (parse-uuid-multiselect [value])
    (into #{}
          (comp (map parse-uuid)
                (filter some?))
          value)))

(defn model! [request]
  (let [congregation (:body (api/get-congregation request {}))
        regions (->> (:regions congregation)
                     (sort-by (comp str :name)
                              (CaseInsensitiveSimpleNaturalComparator/getInstance)))
        territories (->> (:territories congregation)
                         (sort-by (comp str :number)
                                  (CaseInsensitiveSimpleNaturalComparator/getInstance)))
        default-params {:template (:id (first templates))
                        :language (name i18n/*lang*)
                        :mapRaster (:id (first (map-rasters)))
                        :regions (str (:id congregation)) ; congregation boundary is shown first in the regions list
                        :territories (str (:id (first territories)))}]
    (-> {:congregation (-> (select-keys congregation [:id :name])
                           (assoc :locations (->> (:congregationBoundaries congregation)
                                                  (map :location))))
         :regions regions
         :territories territories
         :form (-> (merge default-params (:params request))
                   (select-keys [:template :language :mapRaster :regions :territories])
                   (update :regions parse-uuid-multiselect)
                   (update :territories parse-uuid-multiselect))}
        (merge (map-interaction-help/model request)))))


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

(defn territory-card [territory]
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
        [:div.PrintDateNotice__root--5851a1da
         ;; TODO: find out timezone for current date based on congregation location
         ;;       https://stackoverflow.com/questions/16086962/how-to-get-a-time-zone-from-a-location-using-latitude-and-longitude-coordinates/16086964#16086964
         ;;       https://github.com/RomanIakovlev/timeshape
         [:div.PrintDateNotice__notice--5851a1da "Printed 2024-05-12 with TerritoryBro.com"]
         [:div.PrintDateNotice__content--5851a1da
          ;; TODO: map using web components
          [:div.OpenLayersMap__root--f9d8701d.OpenLayersMap__printout--f9d8701d
           [:div.ol-viewport {:style "position: relative; overflow: hidden; width: 100%; height: 100%;"}
            "[OpenLayersMap]"]]]]]

       [:div {:class (:addresses styles)}
        [:div {:class (:qrCode styles)}
         ;; TODO: QR code using lazy loading with caching
         [:svg {:height "128" :width "128" :viewBox "0 0 29 29" :style "width: 100%; height: auto;"}
          [:path {:fill "#FFFFFF" :d "M0,0 h29v29H0z" :shape-rendering "crispEdges"}]
          [:path {:fill "#000000" :d "M0 0h7v1H0zM11 0h1v1H11zM14 0h1v1H14zM18 0h1v1H18zM22,0 h7v1H22zM0 1h1v1H0zM6 1h1v1H6zM8 1h2v1H8zM13 1h1v1H13zM17 1h1v1H17zM22 1h1v1H22zM28,27 h1v1H28zM0 28h7v1H0zM8 28h1v1H8zM10 28h3v1H10zM14 28h2v1H14zM19 28h1v1H19zM24 28h3v1H24z" :shape-rendering "crispEdges"}]]]
        (:addresses territory)]

       [:div {:class (:footer styles)} (i18n/t "TerritoryCard.footer")]]))))

(defn view [{:keys [congregation territories regions form errors] :as model}]
  (let [errors (group-by first errors)]
    (h/html
     [:div.no-print
      [:h1 (i18n/t "PrintoutPage.title")]

      [:form.pure-form.pure-form-stacked {:method "post"}
       [:fieldset
        [:legend (i18n/t "PrintoutPage.printOptions")]

        [:div.pure-g
         [:div.pure-u-1.pure-u-md-1-2.pure-u-lg-1-3
          [:label {:for "template"} (i18n/t "PrintoutPage.template")]
          [:select#template.pure-input-1 {:name "template"}
           (for [{:keys [id]} templates]
             [:option {:value id
                       :selected (= id (:template form))}
              (i18n/t (str "PrintoutPage.templates." id))])]]]

        [:div.pure-g
         [:div.pure-u-1.pure-u-md-1-2.pure-u-lg-1-3
          [:label {:for "language"} (i18n/t "PrintoutPage.language")]
          [:select#language.pure-input-1 {:name "language"}
           (for [{:keys [code englishName nativeName]} (i18n/languages)]
             [:option {:value code
                       :selected (= code (:language form))}
              nativeName
              (when-not (= nativeName englishName)
                (str " - " englishName))])]]]

        [:div.pure-g
         [:div.pure-u-1.pure-u-md-1-2.pure-u-lg-1-3
          [:label {:for "mapRaster"} (i18n/t "PrintoutPage.mapRaster")]
          [:select#mapRaster.pure-input-1 {:name "mapRaster"}
           (for [{:keys [id name]} (map-rasters)]
             [:option {:value id
                       :selected (= id (:mapRaster form))}
              name])]]]

        ;; TODO: conditional based on selected template
        [:div.pure-g
         [:div.pure-u-1.pure-u-md-1-2.pure-u-lg-1-3
          [:label {:for "regions"} (i18n/t "PrintoutPage.regions")]
          [:select#regions.pure-input-1 {:name "regions"
                                         :multiple true
                                         :size "7"}
           (for [{:keys [id name]} (concat [congregation] regions)]
             [:option {:value id
                       :selected (contains? (:regions form) id)}
              name])]]]

        ;; TODO: conditional based on selected template
        [:div.pure-g
         [:div.pure-u-1.pure-u-md-1-2.pure-u-lg-1-3
          [:label {:for "territories"} (i18n/t "PrintoutPage.territories")]
          [:select#territories.pure-input-1 {:name "territories"
                                             :multiple true
                                             :size "7"}
           (for [{:keys [id number region]} territories]
             [:option {:value id
                       :selected (contains? (:territories form) id)}
              number
              (when-not (str/blank? region)
                (str " - " region))])]]]]

       ;; TODO: submit on form change
       [:button "Submit"]
       [:p (str form)]]]

     ;; TODO: switch language based on selected language
     [:div {:lang "fi"}
      (for [territory territories
            :when (contains? (:territories form) (:id territory))]
        ;; TODO: conditional based on selected template
        (territory-card territory))]

     [:div.no-print
      (map-interaction-help/view model)])))

(defn view! [request]
  (view (model! request)))

(def routes
  ["/congregation/:congregation/printouts"
   {:middleware [[html/wrap-page-path ::page]]}
   [""
    {:name ::page
     :get {:handler (fn [request]
                      (-> (view! request)
                          (layout/page! request)
                          (html/response)))}
     :post {:handler (fn [request]
                       (-> (view! request)
                           (layout/page! request)
                           (html/response)))}}]])
