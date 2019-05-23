;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.congregation
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [territory-bro.config :as config]
            [territory-bro.db :as db]
            [territory-bro.event-store :as event-store]
            [territory-bro.events :as events])
  (:import (java.util UUID)))

(def ^:private query! (db/compile-queries "db/hugsql/congregation.sql"))

(defn- format-congregation [row]
  {:congregation/id (:id row)
   :congregation/name (:name row)
   :congregation/schema-name (:schema_name row)})

(defn get-unrestricted-congregations
  ([conn]
   (get-unrestricted-congregations conn {}))
  ([conn search]
   (->> (query! conn :get-congregations search)
        (map format-congregation)
        (doall))))

(defn get-unrestricted-congregation [conn cong-id]
  (first (get-unrestricted-congregations conn {:ids [cong-id]})))

(defn get-my-congregations
  ([conn user-id]
   (get-my-congregations conn user-id {}))
  ([conn user-id search]
   (get-unrestricted-congregations conn (assoc search
                                               :user user-id))))

(defn get-my-congregation [conn cong-id user-id]
  (first (get-my-congregations conn user-id {:ids [cong-id]})))

(defn use-schema [conn cong-id] ; TODO: create a better helper?
  (let [cong (get-unrestricted-congregation conn cong-id)]
    (db/use-tenant-schema conn (:congregation/schema-name cong))))

(defn create-congregation! [conn name]
  (let [id (UUID/randomUUID)
        master-schema (:database-schema config/env)
        tenant-schema (str master-schema
                           "_"
                           (str/replace (str id) "-" ""))]
    (assert (not (contains? (set (db/get-schemas conn))
                            tenant-schema))
            {:schema-name tenant-schema})
    (event-store/save! conn id 0 [(assoc (events/defaults)
                                         :event/type :congregation.event/congregation-created
                                         :congregation/id id
                                         :congregation/name name
                                         :congregation/schema-name tenant-schema)])
    (query! conn :create-congregation {:id id
                                       :name name
                                       :schema_name tenant-schema})
    (-> (db/tenant-schema tenant-schema master-schema)
        (.migrate))
    (log/info "Congregation created:" id)
    id))

;;;; User access

(defn get-users [conn cong-id]
  (->> (query! conn :get-users {:congregation cong-id})
       (map :user)
       (doall)))

(defn grant-access! [conn cong-id user-id]
  (event-store/save! conn cong-id
                     (count (event-store/read-stream conn cong-id))
                     [(assoc (events/defaults)
                             :event/type :congregation.event/permission-granted
                             :congregation/id cong-id
                             :user/id user-id
                             :permission/id :view-congregation)])
  (query! conn :grant-access {:congregation cong-id
                              :user user-id})
  nil)

(defn revoke-access! [conn cong-id user-id]
  (event-store/save! conn cong-id
                     (count (event-store/read-stream conn cong-id))
                     [(assoc (events/defaults)
                             :event/type :congregation.event/permission-revoked
                             :congregation/id cong-id
                             :user/id user-id
                             :permission/id :view-congregation)])
  (query! conn :revoke-access {:congregation cong-id
                               :user user-id})
  nil)
