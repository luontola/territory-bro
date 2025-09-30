(ns territory-bro.ui.error-page
  (:require [clojure.tools.logging :as log]
            [ring.util.response :as response]
            [territory-bro.ui.hiccup :as h]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.i18n :as i18n]
            [territory-bro.ui.layout :as layout]))

(defn safe-page [request view]
  (let [model (try
                (layout/model! request)
                (catch Throwable t
                  (log/error t "Error in building the layout model")
                  nil))]
    (layout/page view model)))

(defn view [{:keys [status]}]
  (h/html
   [:h1 {} (case (int status)
             403 (i18n/t "Errors.accessDenied")
             404 (i18n/t "Errors.pageNotFound")
             (i18n/t "Errors.unknownError"))]
   [:p [:a {:href "/"}
        (i18n/t "Errors.returnToFrontPage")]]))

(defn page! [request response]
  (safe-page request (view response)))


(def ^:private handled-status-codes #{500 403 404})

(defn- custom-error-page? [response]
  (= "text/html" (response/get-header response "content-type")))

(defn- htmx-request? [request]
  (= "true" (get-in request [:headers "hx-request"])))

(defn wrap-error-pages [handler]
  (fn [request]
    (let [response (handler request)]
      (if (and (contains? handled-status-codes (:status response))
               (not (custom-error-page? response))
               (not (htmx-request? request)))
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
