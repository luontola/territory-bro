(ns territory-bro.ui.forms
  (:require [territory-bro.ui.html :as html]
            [territory-bro.ui.layout :as layout])
  (:import (clojure.lang ExceptionInfo)
           (territory_bro ValidationException)))

(def validation-error-http-status 422)

(defn validation-error-htmx-response [^Exception e request model! view]
  (let [errors (cond
                 (instance? ValidationException e)
                 (.getErrors ^ValidationException e)

                 ;; TODO: stop wrapping ValidationException into ring.util.http-response/bad-request!
                 (instance? ExceptionInfo e)
                 (when (and (= :ring.util.http-response/response (:type (ex-data e)))
                            (= 400 (:status (:response (ex-data e)))))
                   (:errors (:body (:response (ex-data e))))))]
    (if (some? errors)
      (-> (model! request)
          (assoc :errors errors)
          (view)
          (html/response)
          (assoc :status validation-error-http-status))
      (throw e))))

(defn validation-error-page-response [^Exception e request model! view]
  (let [view (fn [model]
               (-> model
                   (view)
                   ;; TODO: Doesn't support {:main-content-variant :full-width}, but it's fine for now, because this
                   ;;       function is not used on any of the full-width pages. Consider passing main-content-variant
                   ;;       via a dynamic binding and setting it in the router to affect all places.
                   (layout/page! request)))]
    (validation-error-htmx-response e request model! view)))
