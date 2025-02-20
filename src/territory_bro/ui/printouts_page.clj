(ns territory-bro.ui.printouts-page
  (:require [clojure.string :as str]
            [ring.util.response :as response]
            [territory-bro.domain.dmz :as dmz]
            [territory-bro.gis.geometry :as geometry]
            [territory-bro.infra.json :as json]
            [territory-bro.infra.middleware :as middleware]
            [territory-bro.ui.hiccup :as h]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.i18n :as i18n]
            [territory-bro.ui.layout :as layout]
            [territory-bro.ui.map-interaction-help :as map-interaction-help]
            [territory-bro.ui.maps :as maps]
            [territory-bro.ui.printout-templates :as printout-templates])
  (:import (io.nayuki.qrcodegen QrCode QrCode$Ecc)
           (java.time Clock Duration LocalDate)
           (territory_bro QrCodeGenerator)))

(def ^:dynamic ^Clock *clock* (Clock/systemUTC))

(def templates
  [{:id "TerritoryCard"
    :fn printout-templates/territory-card
    :type :territory}
   {:id "TerritoryCardMapOnly"
    :fn printout-templates/territory-card-map-only
    :type :territory}
   {:id "NeighborhoodCard"
    :fn printout-templates/neighborhood-card
    :type :territory}
   {:id "RuralTerritoryCard"
    :fn printout-templates/rural-territory-card
    :type :territory}
   {:id "QrCodeOnly"
    :fn printout-templates/qr-code-only
    :type :territory}
   {:id "RegionPrintout"
    :fn printout-templates/region-printout
    :type :region}])

(defn parse-uuid-multiselect [value]
  ;; Ring form params for a <select> element may be a string or a vector of strings,
  ;; depending on whether one or many values were selected. Coerce them all to a set of UUIDs.
  (if (string? value)
    (parse-uuid-multiselect [value])
    (into #{}
          (comp (map (fn [s]
                       (when-not (str/blank? s)
                         (or (parse-uuid s)
                             s)))) ; cong-id can be "demo"
                (filter some?))
          value)))

(defn model! [request]
  (let [cong-id (get-in request [:path-params :congregation])
        _ (when-not (dmz/view-printouts-page? cong-id)
            (dmz/access-denied!))
        congregation (dmz/get-congregation cong-id)
        regions (dmz/list-regions cong-id)
        territories (dmz/list-territories cong-id)
        default-params {:template (:id (first templates))
                        :language (name i18n/*lang*)
                        :map-raster maps/default-for-quality
                        :regions (str (:congregation/id congregation)) ; congregation boundary is shown first in the regions list
                        :territories (str (:territory/id (first territories)))}
        congregation-boundary (dmz/get-congregation-boundary cong-id)]
    (-> {:congregation (-> (select-keys congregation [:congregation/id :congregation/name :congregation/timezone])
                           (assoc :congregation/location congregation-boundary))
         :regions regions
         :territories territories
         :card-minimap-viewports (->> (dmz/list-card-minimap-viewports cong-id)
                                      (mapv :card-minimap-viewport/location))
         ;; TODO: make it a form option that whether to include QR codes on a printout
         :qr-codes-allowed? (dmz/allowed? [:share-territory-link cong-id])
         :form (-> (merge default-params (:params request))
                   (select-keys [:template :language :map-raster :regions :territories])
                   (update :regions parse-uuid-multiselect)
                   (update :territories parse-uuid-multiselect))}
        (merge (map-interaction-help/model request)))))


(defn view [{:keys [congregation territories regions card-minimap-viewports qr-codes-allowed? form] :as model}]
  (let [printout-lang (i18n/validate-lang (keyword (:language form)))
        templates (if qr-codes-allowed?
                    templates
                    (remove #(= "QrCodeOnly" (:id %)) templates))
        template (->> templates
                      (filter #(= (:template form) (:id %)))
                      (first))
        print-date (LocalDate/now (.withZone *clock* (:congregation/timezone congregation)))
        congregation-and-regions (concat [{:region/id (:congregation/id congregation)
                                           :region/name (:congregation/name congregation)
                                           :region/location (:congregation/location congregation)}]
                                         regions)]
    (h/html
     [:div.no-print
      [:h1 {} (i18n/t "PrintoutPage.title")]

      [:form#print-options.pure-form.pure-form-stacked {:hx-post html/*page-path*
                                                        :hx-trigger "load delay:1ms, change delay:250ms"
                                                        :hx-sync "#print-options:replace"
                                                        ;; morph the form to keep form element focus and scroll state
                                                        :hx-select "#print-options"
                                                        :hx-target "this"
                                                        :hx-ext "morph"
                                                        :hx-swap "morph:outerHTML"
                                                        ;; don't morph the printouts, since it would break web components
                                                        :hx-select-oob "#printouts:outerHTML"}
       [:fieldset
        [:legend {} (i18n/t "PrintoutPage.printOptions")]

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
          [:label {:for "language"}
           (i18n/t "PrintoutPage.language")]
          [:select#language.pure-input-1 {:name "language"}
           (for [{:keys [code englishName nativeName]} (i18n/languages)]
             [:option {:value code
                       :selected (= code (:language form))}
              nativeName
              (when-not (= nativeName englishName)
                (str " - " englishName))])]]]

        [:div.pure-g
         [:div.pure-u-1.pure-u-md-1-2.pure-u-lg-1-3
          [:label {:for "map-raster"}
           (i18n/t "PrintoutPage.mapRaster")]
          [:select#map-raster.pure-input-1 {:name "map-raster"}
           (for [{:keys [id name]} (maps/map-rasters)]
             [:option {:value id
                       :selected (= id (:map-raster form))}
              name])]]]

        [:div.pure-g {:style (when-not (= :region (:type template))
                               {:display "none"})}
         [:div.pure-u-1.pure-u-md-1-2.pure-u-lg-1-3
          [:label {:for "regions"}
           (i18n/t "PrintoutPage.regions")]
          [:select#regions.pure-input-1 {:name "regions"
                                         :multiple true
                                         :size "7"}
           (for [{:region/keys [id name]} congregation-and-regions]
             [:option {:value id
                       :selected (contains? (:regions form) id)}
              name])]]]

        [:div.pure-g {:style (when-not (= :territory (:type template))
                               {:display "none"})}
         [:div.pure-u-1.pure-u-md-1-2.pure-u-lg-1-3
          [:label {:for "territories"}
           (i18n/t "PrintoutPage.territories")]
          [:select#territories.pure-input-1 {:name "territories"
                                             :multiple true
                                             :size "7"}
           (for [{:territory/keys [id number region]} territories]
             [:option {:value id
                       :selected (contains? (:territories form) id)}
              number
              (when-not (str/blank? region)
                (str " - " region))])]]]]]]

     [:div#printouts {:lang printout-lang}
      (when-some [template-fn (:fn template)]
        (binding [i18n/*lang* printout-lang]
          (doall ; binding needs eager evaluation
           (case (:type template)
             :territory
             (let [region-boundaries (mapv (comp geometry/parse-wkt :region/location) regions)
                   minimap-viewport-boundaries (mapv geometry/parse-wkt card-minimap-viewports)]
               (for [territory territories
                     :when (contains? (:territories form) (:territory/id territory))]
                 (let [territory-boundary (geometry/parse-wkt (:territory/location territory))
                       enclosing-region (str (geometry/find-enclosing territory-boundary region-boundaries))
                       enclosing-minimap-viewport (str (geometry/find-enclosing territory-boundary minimap-viewport-boundaries))]
                   (template-fn {:territory territory
                                 :congregation-boundary (:congregation/location congregation)
                                 :enclosing-region enclosing-region
                                 :enclosing-minimap-viewport enclosing-minimap-viewport
                                 :map-raster (:map-raster form)
                                 :print-date print-date
                                 :qr-codes-allowed? qr-codes-allowed?}))))

             :region
             (let [territories (->> territories
                                    (mapv (fn [territory]
                                            {:number (:territory/number territory)
                                             :location (:territory/location territory)}))
                                    (json/write-value-as-string))] ; for improved performance, avoid serializing multiple times
               (for [region congregation-and-regions
                     :when (contains? (:regions form) (:region/id region))]
                 (template-fn {:region region
                               :territories territories
                               :map-raster (:map-raster form)
                               :print-date print-date})))))))]

     [:div.no-print {}
      (map-interaction-help/view model)])))

(defn view! [request]
  (view (model! request)))


(defn render-qr-code-svg [text]
  (let [qr-code (QrCode/encodeText text QrCode$Ecc/MEDIUM)]
    (QrCodeGenerator/toSvgString qr-code 0 "white" "black")))

(defn generate-qr-code! [request]
  (let [cong-id (get-in request [:path-params :congregation])
        territory-id (get-in request [:path-params :territory])
        share-url (:url (dmz/generate-qr-code cong-id territory-id))]
    (render-qr-code-svg share-url)))


(def routes
  ["/congregation/:congregation/printouts"
   {:middleware [[html/wrap-page-path ::page]
                 [dmz/wrap-access-check dmz/view-printouts-page?]
                 dmz/wrap-db-connection]}
   [""
    {:name ::page
     :get {:handler (fn [request]
                      (-> (view! request)
                          (layout/page! request {:main-content-variant :full-width})
                          (html/response)))}
     :post {:handler (fn [request]
                       (-> (view! request)
                           (html/response)))}}]

   ["/qr-code/:territory"
    {:get {:handler (fn [request]
                      (-> (generate-qr-code! request)
                          (html/response)
                          ;; avoid generating QR codes unnecessarily while the user is tweaking the settings
                          (response/header "Cache-Control" (str "private, max-age=" (.toSeconds (Duration/ofHours 12)) ", must-revalidate"))
                          ;; this is a GET request to enable caching, but it actually writes new events to the database
                          (assoc ::middleware/mutative-operation? true)))}}]])
