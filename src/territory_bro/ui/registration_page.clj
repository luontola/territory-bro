(ns territory-bro.ui.registration-page
  (:require [ring.util.http-response :as http-response]
            [territory-bro.domain.dmz :as dmz]
            [territory-bro.ui.forms :as forms]
            [territory-bro.ui.hiccup :as h]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.i18n :as i18n]
            [territory-bro.ui.layout :as layout])
  (:import (java.util UUID)))

(defn model! [request]
  (dmz/require-logged-in!)
  {:form (:params request)})

(defn or-divider []
  (h/html
   [:div {:style {:border-bottom "2px solid black"
                  :text-align "center"
                  :margin-top "1em"
                  :margin-bottom "2.5em"}}
    [:span {:style {:position "relative"
                    :top "0.7em"
                    :height "1.5em"
                    :background-color "white"
                    :padding "0 0.5em"
                    :text-transform "uppercase"}}
     (i18n/t "RegistrationPage.or")]]))

(defn join-button []
  (h/html [:a.pure-button {:href "/join"}
           (i18n/t "JoinPage.title")]))

(defn view [model]
  (let [errors (group-by first (:errors model))]
    (h/html
     [:h1 (i18n/t "RegistrationPage.title")]

     [:form.pure-form.pure-form-aligned {:method "post"}
      (html/anti-forgery-field)
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

     (or-divider)
     [:div {:style {:text-align "center"}}
      (join-button)])))

(defn view! [request]
  (view (model! request)))

(defn submit! [request]
  (dmz/require-logged-in!)
  (let [cong-name (get-in request [:params :congregationName])
        cong-id (UUID/randomUUID)]
    (try
      (dmz/dispatch! {:command/type :congregation.command/create-congregation
                      :congregation/id cong-id
                      :congregation/name cong-name})
      (http-response/see-other (str "/congregation/" cong-id))
      (catch Exception e
        (forms/validation-error-page-response e request model! view)))))

(def routes
  ["/register"
   {:get {:handler (fn [request]
                     (-> (view! request)
                         (layout/page! request)
                         (html/response)))}
    :post {:middleware [dmz/wrap-db-connection]
           :handler (fn [request]
                      (submit! request))}}])
