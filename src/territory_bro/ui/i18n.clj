;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.i18n
  (:require [clojure.string :as str]
            [ring.util.response :as response]
            [territory-bro.infra.json :as json]
            [territory-bro.infra.resources :as resources])
  (:import (java.time Duration)
           (java.util Locale Locale$LanguageRange)))

(def default-lang :en)
(def ^:dynamic *lang* default-lang)

(def i18n
  (resources/auto-refresher "i18n.json" #(json/read-value (slurp %))))

(defn t [key]
  (let [path (->> (str/split key #"\.")
                  (map keyword))]
    (or (-> (i18n) :resources *lang* :translation (get-in path))
        key)))

(defn languages []
  (:languages (i18n)))

(defn- validate-lang [lang]
  (if (some? (get-in (i18n) [:resources lang]))
    lang
    default-lang))

(defn- parse-accept-language [request]
  (when-some [ranges (get-in request [:headers "accept-language"])]
    (Locale/lookupTag (Locale$LanguageRange/parse ranges)
                      (map :code (languages)))))

(defn wrap-current-language [handler]
  (fn [request]
    (let [param-lang (get-in request [:params :lang])
          cookie-lang (get-in request [:cookies "lang" :value])
          lang (validate-lang (keyword (or param-lang
                                           cookie-lang
                                           (parse-accept-language request)
                                           default-lang)))]
      (binding [*lang* lang]
        (cond-> (handler request)
          (some? param-lang)
          (response/set-cookie "lang" (name lang) {:max-age (.toSeconds (Duration/ofDays 365))
                                                   :path "/"}))))))
