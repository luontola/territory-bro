;; Copyright © 2015-2018 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.authentication
  (:require [territory-bro.config :refer [env]]
            [territory-bro.permissions :as perm]))

(def ^:dynamic *user*)

(defn user-session [jwt env]
  {::user (assoc (select-keys jwt [:sub :name :email :email_verified :picture])
            :permissions (perm/user-permissions jwt env))})

(defn with-authenticated-user* [request f]
  (binding [*user* (get-in request [:session ::user])]
    (binding [perm/*permissions* (:permissions *user*)]
      (f))))

(defmacro with-authenticated-user [request & body]
  `(with-authenticated-user* ~request (fn [] ~@body)))
