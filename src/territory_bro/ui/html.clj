;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.html
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [net.cgrand.enlive-html :as en]
            [reitit.core :as reitit]
            [ring.middleware.anti-forgery :as anti-forgery]
            [ring.util.http-response :as http-response]
            [ring.util.response :as response]
            [territory-bro.infra.json :as json]
            [territory-bro.ui.hiccup :as h])
  (:import (org.reflections Reflections)
           (org.reflections.scanners Scanners)))

(def ^:dynamic *page-path*)


;;;; Test helpers

(defn normalize-whitespace
  ([s]
   (-> s
       ;; Enlive replaces &nbsp; with \u00a0 before our character entity processing,
       ;; so we need to guard against NBSP also here. It could also originally come as Unicode.
       (str/replace #"[\s\u00a0]+" " ")
       (str/trim)))
  ([s & more]
   (normalize-whitespace (str/join "\n" (cons s more)))))

(defn- visualize-data-test-icon-attribute [node]
  ((en/substitute " " (:data-test-icon (:attrs node)) " " (:content node) " ")))

(defn- visualize-input-element [node]
  (when-not (= "hidden" (:type (:attrs node)))
    ((en/substitute " [" (:value (:attrs node)) "] "))))

(defn- visualize-select-element [node]
  (let [options (:content node)
        first-option (:content (first options))
        selected-options (into []
                               (comp (filter (comp :selected :attrs))
                                     (map :content)
                                     (interpose ", "))
                               options)
        value (cond
                (:multiple (:attrs node)) selected-options
                (empty? selected-options) first-option
                :else selected-options)]
    ((en/substitute " [" value "] "))))

(def ^:private hidden-element? #{:script :template})
(def ^:private inline-element? #{:a :abbr :b :big :cite :code :em :i :small :span :strong :tt})

(defn- strip-html-tags [node]
  (cond
    (string? node) node ; keep text nodes

    (nil? (:tag node)) nil ; strip comments, doctype etc.

    (hidden-element? (:tag node))
    nil

    (inline-element? (:tag node))
    ((en/substitute (:content node)))

    :else
    ((en/substitute " " (:content node) " "))))

(def ^:private html-character-entities
  {"&lt;" "<"
   "&gt;" ">"
   "&amp;" "&"})

(defn visible-text [html]
  (let [nodes (-> (en/html-snippet (str html))
                  (en/transform [(en/attr? :data-test-icon)] visualize-data-test-icon-attribute)
                  (en/transform [:input] visualize-input-element)
                  (en/transform [:select] visualize-select-element)
                  (en/transform [en/any-node] strip-html-tags))]
    (-> (apply str (en/emit* nodes))
        (str/replace #"&\w+;" html-character-entities)
        (normalize-whitespace))))


;;;; Common helpers

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

(def content-hashed-filename #"\.[0-9a-f]{8,40}\.(\w+)$")

(def public-resources
  (let [reflections (Reflections. "public" (into-array [Scanners/Resources]))]
    (->> (.getResources reflections #".*")
         (map (fn [resource-path]
                (let [url (str/replace resource-path #"^public/" "/") ; resource path -> absolute URL
                      wildcard-url (str/replace url content-hashed-filename ".*.$1")] ; "file.hash.ext" -> "file.*.ext"
                  [wildcard-url url])))
         (into {}))))

(defn classes [& ss]
  (->> ss
       (filterv some?)
       (str/join " ")))


;;;; CSRF protection

(defn anti-forgery-token []
  (when (bound? #'anti-forgery/*anti-forgery-token*) ; might not be bound in tests
    (force anti-forgery/*anti-forgery-token*))) ; force may be necessary depending on session strategy

(defn anti-forgery-field []
  (h/html [:input {:type "hidden"
                   :name "__anti-forgery-token"
                   :value (anti-forgery-token)}]))

(defn anti-forgery-headers-json []
  (json/write-value-as-string {:x-csrf-token (anti-forgery-token)}))


;;;; Inline SVG

;; XXX: Enlive always generates HTML and doesn't have a parameter for producing XML (SVG).
;;      Couldn't get clojure.xml/emit-element to work with Enlive's nodes.
;;      If net.cgrand.enlive-html/self-closing-tags were dynamic, we could use binding
;;      to trick Enlive into generating XML. Using alter-var-root as the second best thing.
;;      This may break when using -Dclojure.compiler.direct-linking=true.
(alter-var-root (var en/self-closing-tags) (constantly (constantly true)))
(defn- emit-xml [nodes]
  (apply str (en/emit* nodes)))

(defn- flatten-map [m]
  (mapcat identity m))

(defn inline-svg* [svg-path args]
  {:pre [(string? svg-path)
         (or (nil? args) (map? args))]}
  (when-some [svg-resource (io/resource svg-path)]
    (let [{:keys [class style title]} args
          other-attrs (dissoc args :class :style :title)
          set-data-test-icon-attr (en/set-attr :data-test-icon (str "{" (.getName (io/file svg-path)) "}"))
          set-class-attr (if (some? class)
                           (en/add-class class)
                           identity)
          set-style-attr (if (some? style)
                           (en/set-attr :style (#'hiccup.compiler/render-style-map style))
                           identity)
          set-other-attrs (if-some [kvs (seq (flatten-map other-attrs))]
                            (apply en/set-attr kvs)
                            identity)
          add-title-element (if (some? title)
                              (en/prepend {:tag :title
                                           :content [title]})
                              identity)]
      (-> (en/xml-resource svg-resource)
          (en/transform [:svg] (comp add-title-element
                                     set-other-attrs
                                     set-style-attr
                                     set-class-attr
                                     set-data-test-icon-attr))
          (emit-xml)))))

(defmacro inline-svg
  ([path]
   (when-some [svg (inline-svg* path nil)]
     `(h/raw ~svg)))
  ([path args]
   ;; if the args are dynamic, this macro can't precompute the SVG at compile time
   `(h/raw (inline-svg* ~path ~args))))
