;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.territory-page
  (:require [hiccup2.core :as h]
            [territory-bro.api :as api]
            [territory-bro.ui.css :as css]
            [territory-bro.ui.layout :as layout]))

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
       [:FontAwesomeIcon {:icon "{faShareNodes}"}]
       "{t('TerritoryPage.shareLink.button')}"]

      (when open?
        [:div {:class (:sharePopup styles)}
         ;; TODO: should close popup
         [:button {:type "button"
                   :class (css/classes (:closeButton styles) "pure-button")
                   :onClick "{closePopup}"}
          [:FontAwesomeIcon {:icon "{faXmark}"
                             :title "{t('TerritoryPage.shareLink.closePopup')}/"}
           "{faXmark}"]]

         [:label {:htmlFor "share-link"}
          "{t('TerritoryPage.shareLink.description')}"]

         [:div {:class (:shareLink styles)}
          ;; TODO: should copy link to clipboard
          [:input#share-link {:type "text"
                              :value "{shareUrl}"
                              :aria-readonly "true"}]
          [:button#copy-share-link {:type "button"
                                    :class "pure-button"
                                    :data-clipboard-target "#share-link"}
           [:FontAwesomeIcon {:icon "{faCopy}"
                              :title "{t('TerritoryPage.shareLink.copy')}/"}
            "{faCopy}"]]]])])))


(defn page [request]
  (let [styles (:TerritoryPage (css/modules))
        territory (:body (api/get-territory request))]
    (layout/page {:title "Territory Page"}
      (h/html
       [:DemoDisclaimer]
       [:PageTitle
        [:h1 "TerritoryPage.title " (:number territory)]]

       [:div {:class "pure-g"}
        [:div {:class "pure-u-1 pure-u-sm-2-3 pure-u-md-1-2 pure-u-lg-1-3 pure-u-xl-1-4"}
         [:div {:class (:details styles)}
          [:table {:class "pure-table pure-table-horizontal"}
           [:tbody
            [:tr
             [:th "{t('Territory.number')}"]
             [:td (:number territory)]]
            [:tr
             [:th "{t('Territory.region')}"]
             [:td (:region territory)]]
            [:tr
             [:th "{t('Territory.addresses')}"]
             [:td (:addresses territory)]]
            [:tr
             [:th "{t('TerritoryPage.doNotCalls')}"]
             [:td
              ;; TODO: check if has edit permission
              ;; TODO: toggle edit mode
              [:button {:type "button"
                        :class "pure-button"
                        :style "float: right; font-size: 70%;"}
               "{t('TerritoryPage.edit')}"]
              (or (:doNotCalls territory)
                  "-")]]]]]

         ;; TODO: check if has share permission
         [:div {:class (:actions styles)}
          (share-button {:open? false})]]

        [:div {:class "pure-u-1 pure-u-lg-2-3 pure-u-xl-3-4"}
         [:div {:class (:map styles)}
          [:TerritoryMap {:territory "{territory}"
                          :mapRaster "{mapRaster}"
                          :printout "{false}"
                          :key "{i18n.resolvedLanguage}"}]]
         [:div {:class "no-print"}
          [:MapInteractionHelp]]]]))))
