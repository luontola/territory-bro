(ns territory-bro.ui.i18n
  (:require [ring.util.response :as response]
            [territory-bro.infra.json :as json]
            [territory-bro.infra.resources :as resources])
  (:import (java.time Duration)
           (java.util Locale Locale$LanguageRange)))

(def default-lang :en)
(def ^:dynamic *lang* default-lang)

(defn- flatten-map [m parent-key]
  (reduce-kv
   (fn [result k v]
     (let [new-key (str (when parent-key
                          (str parent-key "."))
                        (name k))]
       (if (map? v)
         (merge result (flatten-map v new-key))
         (assoc result new-key v))))
   {}
   m))

(defn- compile-translation-keys [resources]
  (assert (= [:translation] (keys resources)))
  (flatten-map (:translation resources) nil))

(defn- compile-i18n-json [json-data]
  (update json-data :resources #(update-vals % compile-translation-keys)))

(def i18n
  (resources/auto-refresher "i18n.json" #(compile-i18n-json (json/read-value (slurp %)))))

(defn t ^String [key]
  (let [resources (:resources (i18n))]
    (or (-> resources *lang* (get key))
        (-> resources default-lang (get key))
        key)))

(defn languages []
  (:languages (i18n)))

(defn validate-lang [lang]
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
          (not= cookie-lang (name lang))
          (response/set-cookie "lang" (name lang) {:max-age (.toSeconds (Duration/ofDays 365))
                                                   :path "/"}))))))
