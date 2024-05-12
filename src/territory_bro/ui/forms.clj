;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

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
                   (layout/page! request)))]
    (validation-error-htmx-response e request model! view)))
