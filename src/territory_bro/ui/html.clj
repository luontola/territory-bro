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
      (str/replace #"<input.+?value=\"(.*?)\".*?>" "$1")
      ;; visualize Font Awesome icons
      (str/replace #"<i class=\"(fa-.*?)\"></i>" (fn [[_ class]]
                                                   (let [class (->> (str/split class #" ")
                                                                    (remove font-awesome-icon-styles)
                                                                    (str/join " "))]
                                                     (str "{" class "}"))))
      ;; strip all HTML tags
      (str/replace #"<[^>]*>" " ")
      (normalize-whitespace)))
