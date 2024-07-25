;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.registration-page
  (:require [clojure.string :as str]
            [hiccup2.core :as h]
            [ring.util.http-response :as http-response]
            [territory-bro.api :as api]
            [territory-bro.ui.forms :as forms]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.i18n :as i18n]
            [territory-bro.ui.layout :as layout]))

(defn model! [request]
  (api/require-logged-in!)
  {:form (:params request)})

(defn view [model]
  (let [errors (group-by first (:errors model))]
    (h/html
     [:h1 (i18n/t "RegistrationPage.title")]

     [:form.pure-form.pure-form-aligned {:method "post"}
      [:fieldset
       ;; congregation name field
       (let [error? (contains? errors :missing-name)]
         [:div.pure-control-group
          [:label {:for "congregation-name"}
           (i18n/t "CongregationSettings.congregationName")]
          [:input#congregation-name {:type "text"
                                     :name "congregationName"
                                     :value (-> model :form :congregationName)
                                     :autocomplete "off"
                                     :required true
                                     :aria-invalid (when error? "true")}]
          (when error?
            " ⚠️ ")])

       ;; submit button
       [:div.pure-controls
        [:button.pure-button.pure-button-primary {:type "submit"}
         (i18n/t "RegistrationPage.register")]]]]

     [:p (-> (i18n/t "SupportPage.mailingListAd")
             (str/replace "<0>" "<a href=\"https://groups.google.com/g/territory-bro-announcements\" target=\"_blank\">")
             (str/replace "</0>" "</a>")
             (h/raw))])))

(defn view! [request]
  (view (model! request)))

(defn submit! [request]
  (try
    (let [cong-name (get-in request [:params :congregationName])
          api-request (assoc request :params {:name cong-name})
          cong-id (:id (:body (api/create-congregation api-request)))]
      (http-response/see-other (str "/congregation/" cong-id)))
    (catch Exception e
      (forms/validation-error-page-response e request model! view))))

(def routes
  ["/register"
   {:get {:handler (fn [request]
                     (-> (view! request)
                         (layout/page! request)
                         (html/response)))}
    :post {:handler submit!}}])
