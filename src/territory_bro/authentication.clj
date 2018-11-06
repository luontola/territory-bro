; Copyright © 2015-2018 Esko Luontola
; This software is released under the Apache License 2.0.
; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.authentication
  (:require [territory-bro.config :refer [env]]))

(def ^:dynamic *user*)

(defn save-user [session jwt]
  (assoc session :user (select-keys jwt [:sub :name :email :email_verified :picture])))

(defn with-authenticated-user* [request f]
  (binding [*user* (get-in request [:session :user])]
    (f)))

(defmacro with-authenticated-user [request & body]
  `(with-authenticated-user* ~request (fn [] ~@body)))

(defn super-admin?
  ([]
   (super-admin? *user* env))
  ([user env]
   (if-let [super-admin (env :super-admin)]
     (= (:sub user) super-admin)
     false)))

(defn- permission-to-view-tenant? [id]
  ; TODO: fine-grained authorization
  (super-admin?))

(defn authorized-tenants []
  (->> (keys (env :tenant))
       (filter permission-to-view-tenant?)
       (doall)))
