;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.html
  (:require [clojure.string :as str]
            [reitit.core :as reitit]
            [ring.util.http-response :as http-response]
            [ring.util.response :as response]))

(def ^:dynamic *page-path*)

(defn normalize-whitespace [s]
  (-> s
      (str/replace #"\s+" " ")
      (str/trim)))

(defn- font-awesome-class? [class]
  (str/starts-with? class "fa-"))

(def ^:private font-awesome-icon-styles
  #{"fa-duotone"
    "fa-light"
    "fa-regular"
    "fa-sharp"
    "fa-solid"
    "fa-thin"})

(defn visible-text [html]
  (-> (str html)
      ;; visualize input field's text
      (str/replace #"<input\b[^>]*\bvalue=\"(.*?)\".*?>" "$1")
      ;; visualize select field's selected option
      (str/replace #"<option\b[^>]*\bselected\b.*?>(.*?)</option>" "$1") ; keep selected option
      (str/replace #"<option\b.*?>(.*?)</option>" "") ; remove all other options
      ;; visualize Font Awesome icons
      (str/replace #"<i\b[^>]*\bclass=\"(fa-.*?)\".*?></i>"
                   (fn [[_ class]]
                     (let [class (->> (str/split class #" ")
                                      (filter font-awesome-class?)
                                      (remove font-awesome-icon-styles)
                                      (str/join " "))]
                       (str " {" class "} "))))
      ;; hide template elements
      (str/replace #"<template\b[^>]*>.*?</template>" " ")
      ;; strip all HTML tags
      (str/replace #"<[^>]*>" " ")
      (normalize-whitespace)))

(defn response [html]
  (when (some? html)
    (-> (http-response/ok (str html))
        (response/content-type "text/html"))))

(defn wrap-page-path [handler route-name]
  (fn [request]
    (let [page-path (if (some? route-name)
                      (-> (::reitit/router request)
                          (reitit/match-by-name route-name (:path-params request))
                          (reitit/match->path))
                      (:uri request))]
      (assert (some? page-path))
      (binding [*page-path* page-path]
        (handler request)))))
