;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.html
  (:require [clojure.string :as str]))

(def ^:dynamic *page-path*)

(defn normalize-whitespace [s]
  (-> s
      (str/replace #"\s+" " ")
      (str/trim)))

(defn visible-text [html]
  (-> (str html)
      (str/replace #"<input.+?value=\"(.*?)\".*?>" "$1") ; text field's value -> normal text
      (str/replace #"<[^>]*>" " ")
      (normalize-whitespace)))
