;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.printouts-page
  (:require [clojure.string :as str]
            [hiccup2.core :as h]
            [territory-bro.api :as api]
            [territory-bro.gis.geometry :as geometry]
            [territory-bro.infra.json :as json]
            [territory-bro.infra.resources :as resources]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.i18n :as i18n]
            [territory-bro.ui.layout :as layout]
            [territory-bro.ui.map-interaction-help :as map-interaction-help]
            [territory-bro.ui.printout-templates :as printout-templates])
  (:import (java.time LocalDate ZoneId)
           (net.greypanther.natsort CaseInsensitiveSimpleNaturalComparator)
           (org.locationtech.jts.geom Geometry)))

(def templates
  [{:id "TerritoryCard"
    :fn printout-templates/territory-card
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
                        :territories (str (:id (first territories)))}
        congregation-boundary (->> (:congregationBoundaries congregation)
                                   (mapv (comp geometry/parse-wkt :location))
                                   ;; TODO: precompute the union in the state - there are very few places where the boundaries are handled by ID
                                   (reduce (fn [^Geometry a ^Geometry b]
                                             (.union a b)))
                                   (str))]
    (-> {:congregation (-> (select-keys congregation [:id :name])
                           (assoc :location congregation-boundary)
                           ;; TODO: the timezone could be already precalculated in the state (when it's needed elsewhere, e.g. when recording loans)
                           (assoc :timezone (geometry/timezone-for-location congregation-boundary)))
         :regions regions
         :territories territories
         :card-minimap-viewports (mapv :location (:cardMinimapViewports congregation))
         :form (-> (merge default-params (:params request))
                   (select-keys [:template :language :mapRaster :regions :territories])
                   (update :regions parse-uuid-multiselect)
                   (update :territories parse-uuid-multiselect))}
        (merge (map-interaction-help/model request)))))


(defn view [{:keys [congregation territories regions card-minimap-viewports form] :as model}]
  (let [printout-lang (i18n/validate-lang (keyword (:language form)))
        template (->> templates
                      (filter #(= (:template form) (:id %)))
                      (first))
        print-date (LocalDate/now ^ZoneId (:timezone congregation))]
    (h/html
     [:div.no-print
      [:h1 (i18n/t "PrintoutPage.title")]

      [:form#print-options.pure-form.pure-form-stacked {:method "post"
                                                        :hx-post html/*page-path*
                                                        :hx-trigger "change delay:100ms"

                                                        ;; FIXME: we need hx-sync to avoid earlier request overwriting a later request, but hx-sync causes NPE inside htmx
                                                        ;; htmx.esm.js:721 Uncaught TypeError: Cannot read properties of null (reading 'htmx-internal-data')
                                                        ;;    at getInternalData (htmx.esm.js:721:16)
                                                        ;;    at issueAjaxRequest (htmx.esm.js:4111:17)
                                                        ;;    at htmx.esm.js:2538:13
                                                        ;;    at HTMLFormElement.ot (htmx.esm.js:2444:13)
                                                        #_#_:hx-sync "queue last"

                                                        ;; morph the form to keep form element focus and scroll state
                                                        :hx-select "#print-options"
                                                        :hx-target "this"
                                                        :hx-ext "morph"
                                                        :hx-swap "morph:outerHTML"
                                                        ;; don't morph the printouts, since it would break web components
                                                        :hx-select-oob "#printouts:outerHTML"}
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

        [:div.pure-g {:style (when-not (= :region (:type template))
                               {:display "none"})}
         [:div.pure-u-1.pure-u-md-1-2.pure-u-lg-1-3
          [:label {:for "regions"} (i18n/t "PrintoutPage.regions")]
          [:select#regions.pure-input-1 {:name "regions"
                                         :multiple true
                                         :size "7"}
           (for [{:keys [id name]} (concat [congregation] regions)]
             [:option {:value id
                       :selected (contains? (:regions form) id)}
              name])]]]

        [:div.pure-g {:style (when-not (= :territory (:type template))
                               {:display "none"})}
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
                (str " - " region))])]]]]]]

     [:div#printouts {:lang printout-lang}
      (let [region-boundaries (mapv (comp geometry/parse-wkt :location) regions)
            minimap-viewport-boundaries (mapv geometry/parse-wkt card-minimap-viewports)]
        (for [territory territories
              :when (contains? (:territories form) (:id territory))]
          (binding [i18n/*lang* printout-lang]
            (let [template-fn (:fn template)
                  territory-boundary (geometry/parse-wkt (:location territory))
                  enclosing-region (str (geometry/find-enclosing territory-boundary region-boundaries))
                  enclosing-minimap-viewport (str (geometry/find-enclosing territory-boundary minimap-viewport-boundaries))]
              (when template-fn
                (template-fn {:territory territory
                              :congregation-boundary (:location congregation)
                              :enclosing-region enclosing-region
                              :enclosing-minimap-viewport enclosing-minimap-viewport
                              :map-raster (:mapRaster form)
                              :print-date print-date}))))))]

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
                           (html/response)))}}]])
