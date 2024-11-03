(ns territory-bro.ui.markdown
  (:require [ring.util.http-response :as http-response]
            [territory-bro.ui.hiccup :as h])
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

(defn render-resource [resource]
  (if (some? resource)
    (render (slurp resource))
    (http-response/not-found! "Not found")))
