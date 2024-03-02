;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.error-page
  (:require [clojure.tools.logging :as log]
            [hiccup2.core :as h]
            [ring.util.response :as response]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.i18n :as i18n]
            [territory-bro.ui.layout :as layout]))

(defn safe-layout [request content]
  (let [model (try
                (layout/model! request {})
                (catch Throwable t
                  (log/error t "Error in building the layout model")
                  nil))]
    (str (layout/page model content))))

(defn page [{:keys [status]}]
  (h/html
   [:h1 (case status
          403 (i18n/t "Errors.accessDenied")
          404 (i18n/t "Errors.pageNotFound")
          (i18n/t "Errors.unknownError"))]
   [:p [:a {:href "/"}
        (i18n/t "Errors.returnToFrontPage")]]))

(defn page! [request response]
  (safe-layout request (page response)))

(def handled-status-codes #{500 403 404})

(defn wrap-error-pages [handler]
  (fn [request]
    (let [response (handler request)]
      (if (contains? handled-status-codes (:status response))
        (-> response
            (assoc :body (page! request response))
            (response/content-type "text/html"))
        response))))

(def routes
  ;; helper for easily visualizing the error page
  ["/dev/error/:status"
   {:get {:handler (fn [request]
                     (let [status (-> request :path-params :status parse-long)]
                       (html/response (page! request {:status status}))))}}])
