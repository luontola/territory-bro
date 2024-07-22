;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.sudo-page
  (:require [territory-bro.api :as api]))

(def routes
  ["/sudo"
   {:get {:handler api/sudo}}])
