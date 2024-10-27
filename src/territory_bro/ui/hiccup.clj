;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.hiccup
  (:require [hiccup.compiler :as compiler]
            [hiccup.util :as util]))

(defmacro html [& content]
  (let [html-mode :html
        escape-strings? true]
    (binding [util/*html-mode* html-mode ; compile time options
              util/*escape-strings?* escape-strings?]
      `(binding [util/*html-mode* ~html-mode ; runtime options
                 util/*escape-strings?* ~escape-strings?]
         (util/raw-string ~(apply compiler/compile-html content))))))

(def raw util/raw-string)
