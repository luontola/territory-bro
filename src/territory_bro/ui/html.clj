;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.html
  (:require [clojure.string :as str]
            [hiccup.util :as hiccup.util]
            [reitit.core :as reitit]
            [ring.util.http-response :as http-response]
            [ring.util.response :as response])
  (:import (org.reflections Reflections)
           (org.reflections.scanners Scanners)))

(alter-var-root #'hiccup.util/*html-mode* (constantly :html)) ; change default from :xhtml to :html

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
      ;; custom visualization using data-test-icon attribute
      (str/replace #"<[^<>]+\bdata-test-icon=\"(.*?)\".*?>" " $1 ")
      ;; visualize input field's text
      (str/replace #"<input\b[^>]*\bvalue=\"(.*?)\".*?>" " [$1] ")
      (str/replace #"<input\b.*?>" " [] ")
      ;; visualize select field's selected option
      (str/replace #"<option\b[^>]*\bselected\b.*?>(.*?)</option>" " [$1] ") ; keep selected option
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
      (str/replace #"</?(a|abbr|b|big|cite|code|em|i|small|span|strong|tt)\b.*?>" "") ; inline elements
      (str/replace #"<[^>]*>" " ") ; block elements
      ;; replace HTML character entities
      (str/replace #"&nbsp;" " ")
      (str/replace #"&lt;" "<") ; must be after stripping HTML tags, to avoid creating accidental elements
      (str/replace #"&gt;" ">")
      (str/replace #"&quot;" "\"")
      (str/replace #"&apos;" "'")
      (str/replace #"&amp;" "&") ; must be last, to avoid creating accidental character entities
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

(def public-resources
  (let [reflections (Reflections. "public" (into-array [Scanners/Resources]))]
    (->> (.getResources reflections #".*")
         (map (fn [resource-path]
                (let [url (str/replace resource-path #"^public/" "/") ; resource path -> absolute URL
                      wildcard-url (str/replace url #"-[0-9a-f]{8}\." "-*.")] ; mapping for content-hashed filenames
                  [wildcard-url url])))
         (into {}))))
