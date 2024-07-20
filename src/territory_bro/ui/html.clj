;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.html
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [hiccup.util :as hiccup.util]
            [hiccup2.core :as h]
            [reitit.core :as reitit]
            [ring.util.http-response :as http-response]
            [ring.util.response :as response])
  (:import (org.reflections Reflections)
           (org.reflections.scanners Scanners)))

(alter-var-root #'hiccup.util/*html-mode* (constantly :html)) ; change default from :xhtml to :html

(def ^:dynamic *page-path*)

(defn normalize-whitespace
  ([s]
   (-> s
       (str/replace #"\s+" " ")
       (str/trim)))
  ([s & more]
   (normalize-whitespace (str/join "\n" (cons s more)))))

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

(defn inline-svg* [path args]
  {:pre [(string? path)
         (or (nil? args) (map? args))]}
  (if-some [resource (io/resource path)]
    (let [args-tmp (str (h/html [:svg (-> args
                                          (assoc :data-test-icon (str "{" (.getName (io/file path)) "}"))
                                          (dissoc :class))]))
          args-html (second (re-find #"(<svg.*?)>" args-tmp))]
      ;; TODO: this is getting too complicated - use a HTML/XML transformation library like Enlive or Hickory
      (-> (slurp resource)
          (str/replace #"(class=\".*?)(\")" (if-some [class (:class args)]
                                              (str "$1 "
                                                   (str/escape class {\< "&lt;"
                                                                      \> "&gt;"
                                                                      \& "&amp;"})
                                                   "$2")
                                              "$1$2"))
          (str/replace-first ">" (if-some [title (:title args)]
                                   (str ">" (h/html [:title title]))
                                   ">"))
          (str/replace "<svg" args-html)))
    (log/warn "territory-bro.ui.html/inline-svg: Resource not found:" path)))

(defmacro inline-svg
  ([path]
   (when-some [svg (inline-svg* path nil)]
     `(h/raw ~svg)))
  ([path args]
   ;; if the args are dynamic, this macro can't precompute the SVG at compile time
   `(h/raw (inline-svg* ~path ~args))))
