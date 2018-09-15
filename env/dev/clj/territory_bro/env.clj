; Copyright © 2015-2018 Esko Luontola
; This software is released under the Apache License 2.0.
; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.env
  (:require [selmer.parser :as parser]
            [clojure.tools.logging :as log]
            [territory-bro.dev-middleware :refer [wrap-dev]]))

(def defaults
  {:init
   (fn []
     (parser/cache-off!)
     (log/info "started using development profile"))
   :stop
   (fn []
     (log/info "shut down"))
   :middleware wrap-dev})
