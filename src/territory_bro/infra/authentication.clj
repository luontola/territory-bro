;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.infra.authentication
  (:import (java.util UUID)))

(def user-profile-keys [:sub :name :nickname :email :email_verified :picture])
(def anonymous-user-id (UUID. 0 0))
(def anonymous-user {:user/id anonymous-user-id})

(def ^:dynamic *user* anonymous-user)

(defn current-user-id []
  (let [user-id (:user/id *user*)]
    (assert (uuid? user-id))
    user-id))

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

(defmacro with-anonymous-user [& body]
  `(binding [*user* anonymous-user]
     ~@body))

(defmacro with-user [user & body]
  `(binding [*user* (let [user# ~user]
                      (assert (map? user#) (pr-str user#))
                      (assert (uuid? (:user/id user#)) (pr-str user#))
                      user#)]
     ~@body))

(defmacro with-user-id [user-id & body]
  `(with-user {:user/id ~user-id}
     ~@body))

(defmacro with-user-from-session [request & body]
  `(with-user (or (get-in ~request [:session ::user])
                  anonymous-user)
     ~@body))

(defn wrap-current-user [handler]
  (fn [request]
    (with-user-from-session request
      (handler request))))
