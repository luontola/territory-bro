;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.visible)

(defn printouts-page? [permissions]
  (true? (:viewCongregation permissions)))

(defn settings-page? [permissions]
  (true? (or (:configureCongregation permissions)
             (:gisAccess permissions))))
