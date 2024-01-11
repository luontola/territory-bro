;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.territory-page
  (:require [clojure.string :as str]
            [hiccup2.core :as h]
            [territory-bro.api :as api]
            [territory-bro.ui.css :as css]
            [territory-bro.ui.i18n :as i18n]
            [territory-bro.ui.layout :as layout])
  (:import (java.util UUID)))

(def do-not-calls-was-updated "do-not-calls-was-updated")

(defn view-do-not-calls [request]
  (let [cong-id (UUID/fromString (get-in request [:params :congregation]))
        territory-id (UUID/fromString (get-in request [:params :territory]))
        territory (:body (api/get-territory request))]
    (h/html
     [:div {:hx-target "this"
            :hx-swap "outerHTML"}
      ;; TODO: check if has edit permission
      [:button {:hx-post (str "/congregation/" cong-id "/territories/" territory-id "/edit-do-not-calls")
                :hx-disabled-elt "this"
                :type "button"
                :class "pure-button"
                :style "float: right; font-size: 70%;"}
       (i18n/t "TerritoryPage.edit")]
      (or (:doNotCalls territory)
          "-")])))

(defn edit-do-not-calls [request]
  (let [cong-id (UUID/fromString (get-in request [:params :congregation]))
        territory-id (UUID/fromString (get-in request [:params :territory]))
        territory (:body (api/get-territory request))]
    (h/html
     [:form.do-not-calls {:hx-post (str "/congregation/" cong-id "/territories/" territory-id "/save-do-not-calls")
                          :hx-disabled-elt ".do-not-calls :is(textarea, button)"
                          :hx-target "this"
                          :hx-swap "outerHTML"}
      [:textarea {:name "do-not-calls"}
       (:doNotCalls territory)]
      [:button {:type "submit"}
       "Save"]])))

(defn save-do-not-calls [request]
  (api/edit-do-not-calls request)
  (h/html (view-do-not-calls request)))

(defn another-field [request]
  (let [cong-id (UUID/fromString (get-in request [:params :congregation]))
        territory-id (UUID/fromString (get-in request [:params :territory]))
        territory (:body (api/get-territory request))]
    (h/html
     [:div {:hx-target "this"
            :hx-swap "outerHTML"
            :hx-trigger (str do-not-calls-was-updated " from:body")
            :hx-get (str "/congregation/" cong-id "/territories/" territory-id "/another-field")}
      "Another field: "
      (:doNotCalls territory)])))

(defn share-button [{:keys [open?]}]
  (let [styles (:TerritoryPage (css/modules))]
    (h/html
     [:form {:class "pure-form"}
      ;; TODO: should toggle popup
      [:button {:type "button"
                :class (css/classes "pure-button"
                                    (when open?
                                      "pure-button-active"))
                :aria-expanded (if open? "true" "false")}
       [:FontAwesomeIcon {:icon "{faShareNodes}"}
        (i18n/t "TerritoryPage.shareLink.button")]]

      (when open?
        [:div {:class (:sharePopup styles)}
         ;; TODO: should close popup
         [:button {:type "button"
                   :class (css/classes (:closeButton styles) "pure-button")
                   :onClick "{closePopup}"}
          [:FontAwesomeIcon {:icon "{faXmark}"
                             :title (i18n/t "TerritoryPage.shareLink.closePopup")}
           "{faXmark}"]]

         [:label {:htmlFor "share-link"}
          (i18n/t "TerritoryPage.shareLink.description")]

         [:div {:class (:shareLink styles)}
          ;; TODO: should copy link to clipboard
          [:input#share-link {:type "text"
                              :value "{shareUrl}"
                              :aria-readonly "true"}]
          [:button#copy-share-link {:type "button"
                                    :class "pure-button"
                                    :data-clipboard-target "#share-link"}
           [:FontAwesomeIcon {:icon "{faCopy}"
                              :title (i18n/t "TerritoryPage.shareLink.copy")}
            "{faCopy}"]]]])])))



(defn page [request]
  (let [cong-id (UUID/fromString (get-in request [:params :congregation]))
        territory-id (UUID/fromString (get-in request [:params :territory]))
        styles (:TerritoryPage (css/modules))
        territory (:body (api/get-territory request))]
    (layout/page {:title "Territory Page"}
      (h/html
       [:DemoDisclaimer]
       [:PageTitle
        [:h1 (-> (i18n/t "TerritoryPage.title")
                 (str/replace "{{number}}" (:number territory)))]]
       [:div {:class "pure-g"}
        [:div {:class "pure-u-1 pure-u-sm-2-3 pure-u-md-1-2 pure-u-lg-1-3 pure-u-xl-1-4"}
         [:div {:class (:details styles)}
          [:table {:class "pure-table pure-table-horizontal"}
           [:tbody
            [:tr
             [:th (i18n/t "Territory.number")]
             [:td (:number territory)]]
            [:tr
             [:th (i18n/t "Territory.region")]
             [:td (:region territory)]]
            [:tr
             [:th (i18n/t "Territory.addresses")]
             [:td (:addresses territory)]]
            [:tr
             [:th (i18n/t "TerritoryPage.doNotCalls")]
             [:td (view-do-not-calls request)]]]]]

         ;; TODO: check if has share permission
         [:div {:class (:actions styles)}
          (share-button {:open? false})]]

        (another-field request)

        [:div {:class "pure-u-1 pure-u-lg-2-3 pure-u-xl-3-4"}
         [:div {:class (:map styles)}
          [:TerritoryMap {:territory "{territory}"
                          :mapRaster "{mapRaster}"
                          :printout "{false}"
                          :key "{i18n.resolvedLanguage}"}]]
         [:div {:class "no-print"}
          [:MapInteractionHelp]]]]))))
