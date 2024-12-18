(ns territory-bro.domain.do-not-calls
  (:require [clojure.string :as str]
            [territory-bro.infra.db :as db]))

(def ^:private queries (db/compile-queries "db/hugsql/do-not-calls.sql"))


;;;; Queries

(defn- parse-db-row [row]
  (when (some? row)
    {:congregation/id (:congregation row)
     :territory/id (:territory row)
     :territory/do-not-calls (:do_not_calls row)
     :do-not-calls/last-modified (:last_modified row)}))

(defn ^:dynamic get-do-not-calls [conn cong-id territory-id]
  (-> (db/query! conn queries :get-do-not-calls {:congregation cong-id
                                                 :territory territory-id})
      (parse-db-row)))


;;;; Command handlers

(defmulti ^:private command-handler (fn [command _state _injections]
                                      (:command/type command)))

(defmethod command-handler :do-not-calls.command/save-do-not-calls
  [command _state {:keys [conn check-permit]}]
  (let [cong-id (:congregation/id command)
        territory-id (:territory/id command)
        do-not-calls (:territory/do-not-calls command)]
    (check-permit [:edit-do-not-calls cong-id territory-id])
    (if (str/blank? do-not-calls)
      (db/query! conn queries :delete-do-not-calls {:congregation cong-id
                                                    :territory territory-id})
      (db/query! conn queries :save-do-not-calls {:congregation cong-id
                                                  :territory territory-id
                                                  :do_not_calls do-not-calls
                                                  :last_modified (:command/time command)})))
  nil)

(defn handle-command [command state injections]
  (command-handler command state injections))
