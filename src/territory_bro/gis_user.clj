;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.gis-user
  (:require [territory-bro.db :as db]
            [clojure.test :refer [deftest is]])
  (:import (java.util Base64)
           (java.security SecureRandom)))

(def ^:private query! (db/compile-queries "db/hugsql/gis-user.sql"))

(defn generate-password [length]
  (let [bytes (byte-array length)]
    (-> (SecureRandom/getInstanceStrong)
        (.nextBytes bytes))
    (-> (Base64/getUrlEncoder)
        (.encodeToString bytes)
        (.substring 0 length))))

(defn- uuid-prefix [uuid]
  (-> (str uuid)
      (.replace "-" "")
      (.substring 0 16)))

(defn create-gis-user! [conn cong-id user-id]
  (let [username (str "gis_user_" (uuid-prefix cong-id) "_" (uuid-prefix user-id))
        password (generate-password 50)]
    (query! conn :create-gis-user {:congregation cong-id
                                   :user user-id
                                   :username username
                                   :password password})
    nil))

(defn delete-gis-user! [conn cong-id user-id]
  (query! conn :delete-gis-user {:congregation cong-id
                                 :user user-id}))

(defn secret [str]
  (fn [] str))

(defn- format-gis-user [gis-user]
  {::congregation (:congregation gis-user)
   ::user (:user gis-user)
   ::username (:username gis-user)
   ::password (secret (:password gis-user))})

(defn get-gis-users
  ([conn]
   (get-gis-users conn {}))
  ([conn search]
   (->> (query! conn :get-gis-users search)
        (map format-gis-user)
        (doall))))

(defn get-gis-user [conn cong-id user-id]
  (first (get-gis-users conn {:congregation cong-id
                              :user user-id})))


