;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.gis-user-process
  (:require [territory-bro.todo-tracker :as todo-tracker]
            [territory-bro.util :refer [conj-set]]))

(defmulti projection (fn [_state event] (:event/type event)))
(defmethod projection :default [state _event] state)

(defn- gis-user-key [event]
  (select-keys event [:congregation/id :user/id]))

(defmethod projection :congregation.event/permission-granted
  [state event]
  (if (= :gis-access (:permission/id event))
    (let [k (gis-user-key event)]
      (-> state
          (todo-tracker/merge-state ::gis-user k k)
          (todo-tracker/set-desired ::gis-user k :present)))
    state))

(defmethod projection :congregation.event/permission-revoked
  [state event]
  (if (= :gis-access (:permission/id event))
    (-> state
        (todo-tracker/set-desired ::gis-user (gis-user-key event) :absent))
    state))

(defmethod projection :congregation.event/gis-user-created
  [state event]
  (-> state
      (todo-tracker/set-actual ::gis-user (gis-user-key event) :present)))

(defmethod projection :congregation.event/gis-user-deleted
  [state event]
  (-> state
      (todo-tracker/set-actual ::gis-user (gis-user-key event) :absent)))


(def ^:private system (str (ns-name *ns*)))

(defn sort-users [gis-users]
  (sort-by (juxt :congregation/id :user/id) gis-users))

(defn generate-commands [state {:keys [now]}]
  (concat
   (for [gis-user (sort-users (todo-tracker/creatable state ::gis-user))]
     {:command/type :gis-user.command/create-gis-user
      :command/time (now)
      :command/system system
      :congregation/id (:congregation/id gis-user)
      :user/id (:user/id gis-user)})
   (for [gis-user (sort-users (todo-tracker/deletable state ::gis-user))]
     {:command/type :gis-user.command/delete-gis-user
      :command/time (now)
      :command/system system
      :congregation/id (:congregation/id gis-user)
      :user/id (:user/id gis-user)})))
