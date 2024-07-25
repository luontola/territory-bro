;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.join-page
  (:require [hiccup2.core :as h]
            [territory-bro.api :as api]
            [territory-bro.infra.authentication :as auth]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.i18n :as i18n]
            [territory-bro.ui.layout :as layout]))

(defn model! [_request]
  (api/require-logged-in!)
  {:user-id (:user/id auth/*user*)})

(defn view [{:keys [user-id]}]
  (h/html
   [:h1 (i18n/t "JoinPage.title")]
   [:p (-> (i18n/t "JoinPage.introduction")
           (h/raw))]
   [:p (i18n/t "JoinPage.yourUserId")]
   [:p#your-user-id {:style {:font-size "150%"
                             :margin "15px"}}
    user-id]
   [:p [:button#copy-your-user-id.pure-button {:type "button"
                                               :data-clipboard-target "#your-user-id"}
        (i18n/t "JoinPage.copy")]]))

(defn view! [request]
  (view (model! request)))

(def routes
  ["/join"
   {:get {:handler (fn [request]
                     (-> (view! request)
                         (layout/page! request)
                         (html/response)))}}])
