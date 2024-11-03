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

(def ^{:arglists '([& xs])} raw util/raw-string)
