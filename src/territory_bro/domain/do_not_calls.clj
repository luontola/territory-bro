;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.domain.do-not-calls
  (:require [clojure.string :as str]
            [territory-bro.infra.db :as db]))

(def ^:private query! (db/compile-queries "db/hugsql/do-not-calls.sql"))

(defn- parse-db-row [row]
  (when (some? row)
    {:congregation/id (:congregation row)
     :territory/id (:territory row)
     :territory/do-not-calls (:do_not_calls row)
     :do-not-calls/last-modified (:last_modified row)}))

(defn ^:dynamic get-do-not-calls [conn cong-id territory-id]
  (-> (query! conn :get-do-not-calls {:congregation cong-id
                                      :territory territory-id})
      (parse-db-row)))

(defn save-do-not-calls! [conn command]
  (let [do-not-calls (:territory/do-not-calls command)]
    (if (str/blank? do-not-calls)
      (query! conn :delete-do-not-calls {:congregation (:congregation/id command)
                                         :territory (:territory/id command)})
      (query! conn :save-do-not-calls {:congregation (:congregation/id command)
                                       :territory (:territory/id command)
                                       :do_not_calls do-not-calls
                                       :last_modified (:command/time command)}))))
