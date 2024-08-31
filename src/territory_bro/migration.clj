;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.migration
  (:require [territory-bro.domain.congregation :as congregation]
            [territory-bro.infra.config :as config]))

(defn- is-admin? [permissions]
  (contains? permissions :configure-congregation))

(def ^:private all-permissions-set (set congregation/all-permissions))
(defn- all-permissions? [permissions]
  (= all-permissions-set permissions))

(def ^:private system (str (ns-name *ns*)))

(defn generate-commands [state]
  (for [cong (congregation/get-unrestricted-congregations state)
        [user-id permissions] (:congregation/user-permissions cong)
        :when (and (is-admin? permissions)
                   (not (all-permissions? permissions)))]
    {:command/type :congregation.command/set-user-permissions
     :command/time (config/now)
     :command/system system
     :congregation/id (:congregation/id cong)
     :user/id user-id
     :permission/ids congregation/all-permissions}))
