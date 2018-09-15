; Copyright © 2015-2018 Esko Luontola
; This software is released under the Apache License 2.0.
; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.config
  (:require [cprop.core :refer [load-config]]
            [cprop.source :as source]
            [mount.core :as mount]))

(mount/defstate env
  :start
  (load-config :merge [(mount/args)
                       (source/from-system-props)
                       (source/from-env)]))
