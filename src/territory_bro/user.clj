;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.user
  (:require [clojure.tools.logging :as log]
            [territory-bro.db :as db])
  (:import (java.util UUID)))

(def ^:private query! (db/compile-queries "db/hugsql/user.sql"))

;; TODO: create if not exists
(defn create-user! [conn subject attributes]
  (let [id (UUID/randomUUID)]
    (query! conn :create-user {:id id
                               :subject subject
                               :attributes attributes})
    (log/info "User created:" id)
    id))
