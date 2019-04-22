;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.congregation
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [territory-bro.config :as config]
            [territory-bro.db :as db]
            [territory-bro.permissions :as perm])
  (:import (java.util UUID)))

(defn- format-tenant [id]
  ; TODO: human-readable name
  {:id id
   :name (.toUpperCase (name id))})

(defn my-congregations []
  (->> (perm/visible-congregations)
       (map format-tenant)
       (sort-by #(.toUpperCase (:name %)))))

;; new stuff

(def ^:private query! (db/compile-queries "db/hugsql/congregation.sql"))

(defn- format-congregation [row]
  {::id (:id row)
   ::name (:name row)
   ::schema-name (:schema_name row)})

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

(defn create-congregation! [conn name]
  (let [id (UUID/randomUUID)
        schema-name (str (:database-schema config/env)
                         "_"
                         (str/replace (str id) "-" ""))]
    (assert (not (contains? (set (db/get-schemas conn))
                            schema-name))
            {:schema-name schema-name})
    (query! conn :create-congregation {:id id
                                       :name name
                                       :schema_name schema-name})
    (-> (db/tenant-schema schema-name)
        (.migrate))
    (log/info "Congregation created:" id)
    id))

;;;; User access

(defn get-users [conn cong-id]
  (->> (query! conn :get-users {:congregation cong-id})
       (map :user)
       (doall)))

(defn grant-access! [conn cong-id user-id]
  (query! conn :grant-access {:congregation cong-id
                              :user user-id})
  nil)

(defn revoke-access! [conn cong-id user-id]
  (query! conn :revoke-access {:congregation cong-id
                               :user user-id})
  nil)
