;; Copyright © 2015-2020 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.infra.authentication
  (:require [territory-bro.infra.config :refer [env]]))

(def ^:dynamic *user*)

(def user-profile-keys [:sub :name :nickname :email :email_verified :picture])

(defn user-session [jwt user-id]
  {::user (-> (select-keys jwt user-profile-keys)
              (assoc :user/id user-id))})

(defn with-authenticated-user* [request f]
  (binding [*user* (get-in request [:session ::user])]
    (f)))

(defmacro with-authenticated-user [request & body]
  `(with-authenticated-user* ~request (fn [] ~@body)))
