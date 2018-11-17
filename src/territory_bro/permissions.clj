; Copyright © 2015-2018 Esko Luontola
; This software is released under the Apache License 2.0.
; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.permissions
  (:require [territory-bro.util :refer [getx]]))

(def ^:dynamic *permissions*)

(defn- congregation-permissions [user-id congregation-id env]
  (let [super-admin (get env :super-admin)
        congregation-admins (get-in env [:tenant congregation-id :admins])]
    (if (or (= super-admin user-id)
            (contains? congregation-admins user-id))
      #{:view-territories}
      nil)))

(defn user-permissions [jwt env]
  (let [user-id (getx jwt :sub)]
    (->> (keys (get env :tenant))
         (mapcat (fn [congregation-id]
                   (if-let [permissions (congregation-permissions user-id congregation-id env)]
                     [[congregation-id permissions]])))
         (into {}))))

(defn visible-congregations
  ([]
   (visible-congregations *permissions*))
  ([permissions]
   (keys permissions)))

(defn can-view-territories?
  ([congregation-id]
   (can-view-territories? congregation-id *permissions*))
  ([congregation-id permissions]
   (boolean (get-in permissions [congregation-id :view-territories]))))
