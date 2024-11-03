(ns territory-bro.infra.user
  (:require [territory-bro.infra.db :as db])
  (:import (java.util UUID)
           (territory_bro ValidationException)))

(def ^:private queries (db/compile-queries "db/hugsql/user.sql"))

(defn- format-user [user]
  {:user/id (:id user)
   :user/subject (:subject user)
   :user/attributes (-> (:attributes user)
                        ;; avoid repeating the subject needlessly - the canonical value is the one in :user/subject
                        (dissoc :sub))})

(defn ^:dynamic get-users
  ([conn]
   (get-users conn {}))
  ([conn search]
   (->> (db/query! conn queries :get-users search)
        (mapv format-user))))

(defn get-by-id [conn user-id]
  (first (get-users conn {:ids [user-id]})))

(defn get-by-subject [conn subject]
  (first (get-users conn {:subjects [subject]})))

(defn save-user! [conn subject attributes]
  (:id (first (db/query! conn queries :save-user {:id (UUID/randomUUID)
                                                  :subject subject
                                                  :attributes attributes}))))

(defn ^:dynamic check-user-exists [conn user-id]
  (when (nil? (get-by-id conn user-id))
    (throw (ValidationException. [[:no-such-user user-id]]))))
