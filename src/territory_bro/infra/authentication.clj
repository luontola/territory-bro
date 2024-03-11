;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.infra.authentication
  (:import (java.util UUID)))

(def ^:dynamic *user*)

(def user-profile-keys [:sub :name :nickname :email :email_verified :picture])
(def anonymous-user-id (UUID. 0 0))
(def ^:private anonymous-user {:user/id anonymous-user-id})

(defn anonymous?
  ([]
   (anonymous? (:user/id *user*)))
  ([user-id]
   (or (nil? user-id)
       (= anonymous-user-id user-id))))

(defn logged-in?
  ([]
   (not (anonymous?)))
  ([user-id]
   (not (anonymous? user-id))))

(defn user-session [jwt user-id]
  {::user (-> (select-keys jwt user-profile-keys)
              (assoc :user/id user-id))})

(defn with-user* [request f]
  (binding [*user* (or (get-in request [:session ::user])
                       anonymous-user)]
    (f)))

(defmacro with-user-from-session [request & body]
  `(with-user* ~request (fn [] ~@body)))
