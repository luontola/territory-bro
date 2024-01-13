;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.territory-page
  (:require [clojure.string :as str]
            [hiccup2.core :as h]
            [territory-bro.api :as api]
            [territory-bro.ui.css :as css]
            [territory-bro.ui.i18n :as i18n]))

(def ^:dynamic *page-path*)

(defn do-not-calls--view [territory]
  (h/html
   [:div {:hx-target "this"
          :hx-swap "outerHTML"}
    ;; TODO: check if has edit permission
    [:button.pure-button {:hx-post (str *page-path* "/do-not-calls/edit")
                          :hx-disabled-elt "this"
                          :type "button"
                          :style "float: right; font-size: 70%;"}
     (i18n/t "TerritoryPage.edit")]
    (or (:doNotCalls territory)
        "-")]))

(defn do-not-calls--edit [territory]
  (h/html
   [:form.do-not-calls.pure-form {:hx-target "this"
                                  :hx-swap "outerHTML"
                                  :hx-post (str *page-path* "/do-not-calls/save")
                                  :hx-disabled-elt ".do-not-calls :is(textarea, button)"}
    [:textarea.pure-input-1 {:name "do-not-calls"
                             :rows 5
                             :autofocus true}
     (:doNotCalls territory)]
    [:button.pure-button.pure-button-primary {:type "submit"}
     (i18n/t "TerritoryPage.save")]]))

(defn do-not-calls--save! [request]
  (api/edit-do-not-calls request)
  (let [territory (:body (api/get-territory request))]
    (do-not-calls--view territory)))


(defn share-link [{:keys [open? link]}]
  (let [styles (:TerritoryPage (css/modules))]
    (h/html
     [:form.pure-form {:hx-target "this"
                       :hx-swap "outerHTML"}
      [:button.pure-button {:hx-post (str *page-path* "/share-link/" (if open? "close" "open"))
                            :type "button"
                            :class (when open?
                                     "pure-button-active")
                            :aria-expanded (if open? "true" "false")}
       [:FontAwesomeIcon {:icon "{faShareNodes}"}
        (i18n/t "TerritoryPage.shareLink.button")]]

      (when open?
        [:div {:class (:sharePopup styles)}
         [:button.pure-button {:hx-post (str *page-path* "/share-link/close")
                               :type "button"
                               :class (:closeButton styles)}
          [:FontAwesomeIcon {:icon "{faXmark}"
                             :title (i18n/t "TerritoryPage.shareLink.closePopup")}
           "{faXmark}"]]

         [:label {:htmlFor "share-link"}
          (i18n/t "TerritoryPage.shareLink.description")]

         [:div {:class (:shareLink styles)}
          [:input#share-link {:type "text"
                              :value link
                              :readonly true
                              :style "color: unset; background-color: unset;"}]
          ;; TODO: should copy link to clipboard
          [:button#copy-share-link.pure-button {:type "button"
                                                :data-clipboard-target "#share-link"}
           [:FontAwesomeIcon {:icon "{faCopy}"
                              :title (i18n/t "TerritoryPage.shareLink.copy")}
            "{faCopy}"]]]])])))

(defn share-link--open! [request]
  ;; TODO: should cache the share, to avoid creating new ones when clicking the share button repeatedly
  (let [share (:body (api/share-territory-link request))]
    (share-link {:open? true
                 :link (:url share)})))

(defn share-link--close []
  (share-link {:open? false}))


(defn page [territory]
  (let [styles (:TerritoryPage (css/modules))]
    (h/html
     [:DemoDisclaimer]
     [:PageTitle
      [:h1 (-> (i18n/t "TerritoryPage.title")
               (str/replace "{{number}}" (:number territory)))]]
     [:div.pure-g
      [:div.pure-u-1.pure-u-sm-2-3.pure-u-md-1-2.pure-u-lg-1-3.pure-u-xl-1-4
       [:div {:class (:details styles)}
        [:table.pure-table.pure-table-horizontal
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
           [:td (do-not-calls--view territory)]]]]]

       ;; TODO: check if has share permission
       [:div {:class (:actions styles)}
        (share-link--close)]]

      [:div.pure-u-1.pure-u-lg-2-3.pure-u-xl-3-4
       [:div {:class (:map styles)}
        [:TerritoryMap {:territory "{territory}"
                        :mapRaster "{mapRaster}"
                        :printout "{false}"
                        :key "{i18n.resolvedLanguage}"}]]
       [:div.no-print
        [:MapInteractionHelp]]]])))
