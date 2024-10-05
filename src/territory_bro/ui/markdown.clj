;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.markdown
  (:require [clojure.java.io :as io]
            [hiccup2.core :as h]
            [ring.util.http-response :as http-response])
  (:import (com.vladsch.flexmark.ext.anchorlink AnchorLinkExtension)
           (com.vladsch.flexmark.html HtmlRenderer)
           (com.vladsch.flexmark.parser Parser)
           (com.vladsch.flexmark.util.data MutableDataSet)))

(defn render [^String markdown]
  (let [options (doto (MutableDataSet.)
                  (.set Parser/EXTENSIONS [(AnchorLinkExtension/create)]))
        parser (.build (Parser/builder options))
        renderer (.build (HtmlRenderer/builder options))
        document (.parse parser markdown)]
    (h/raw (.render renderer document))))

(defn render-resource [resource-path]
  (if-some [resource (io/resource resource-path)]
    (render (slurp resource))
    (http-response/not-found! "Not found")))
