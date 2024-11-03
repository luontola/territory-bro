(ns territory-bro.gis.gis-user-process
  (:require [territory-bro.infra.config :as config]
            [territory-bro.infra.presence-tracker :as presence-tracker]))

(defmulti projection (fn [_state event] (:event/type event)))
(defmethod projection :default [state _event] state)

(defn- gis-user-key [event]
  (select-keys event [:congregation/id :user/id]))

(defmethod projection :congregation.event/permission-granted
  [state event]
  (if (= :gis-access (:permission/id event))
    (-> state
        (presence-tracker/set-desired ::tracked-gis-users (gis-user-key event) :present))
    state))

(defmethod projection :congregation.event/permission-revoked
  [state event]
  (if (= :gis-access (:permission/id event))
    (-> state
        (presence-tracker/set-desired ::tracked-gis-users (gis-user-key event) :absent))
    state))

(defmethod projection :congregation.event/gis-user-created
  [state event]
  (-> state
      (presence-tracker/set-actual ::tracked-gis-users (gis-user-key event) :present)))

(defmethod projection :congregation.event/gis-user-deleted
  [state event]
  (-> state
      (presence-tracker/set-actual ::tracked-gis-users (gis-user-key event) :absent)))


(def ^:private system (str (ns-name *ns*)))

(defn- sort-users [gis-users]
  (sort-by (juxt :congregation/id :user/id) gis-users))

(defn generate-commands [state]
  (concat
   (for [gis-user (sort-users (presence-tracker/creatable state ::tracked-gis-users))]
     {:command/type :gis-user.command/create-gis-user
      :command/time (config/now)
      :command/system system
      :congregation/id (:congregation/id gis-user)
      :user/id (:user/id gis-user)})
   (for [gis-user (sort-users (presence-tracker/deletable state ::tracked-gis-users))]
     {:command/type :gis-user.command/delete-gis-user
      :command/time (config/now)
      :command/system system
      :congregation/id (:congregation/id gis-user)
      :user/id (:user/id gis-user)})))
