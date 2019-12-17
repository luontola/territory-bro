;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.gis-user-process)

(defmulti projection (fn [_state event] (:event/type event)))
(defmethod projection :default [state _event] state)

;; TODO: extract shared code for tracking desired vs actual
(defn- update-todo [state event]
  (let [key (select-keys event [:congregation/id :user/id])
        {:keys [desired actual]
         :or {desired :absent
              actual :absent}} (get-in state [::gis-users key])
        action (cond
                 (and (= :present desired) (= :absent actual)) :create
                 (and (= :absent desired) (= :present actual)) :delete
                 :else :ignore)]
    (case action
      :create (-> state
                  (update ::gis-users-to-be-created (fnil conj #{}) key)
                  (update ::gis-users-to-be-deleted disj key))
      :delete (-> state
                  (update ::gis-users-to-be-created disj key)
                  (update ::gis-users-to-be-deleted (fnil conj #{}) key))
      :ignore (-> state
                  (update ::gis-users-to-be-created disj key)
                  (update ::gis-users-to-be-deleted disj key)))))

(defmethod projection :congregation.event/permission-granted
  [state event]
  (if (= :gis-access (:permission/id event))
    (-> state
        (assoc-in [::gis-users (select-keys event [:congregation/id :user/id]) :desired] :present)
        (update-todo event))
    state))

(defmethod projection :congregation.event/permission-revoked
  [state event]
  (if (= :gis-access (:permission/id event))
    (-> state
        (assoc-in [::gis-users (select-keys event [:congregation/id :user/id]) :desired] :absent)
        (update-todo event))
    state))

(defmethod projection :congregation.event/gis-user-created
  [state event]
  (-> state
      (assoc-in [::gis-users (select-keys event [:congregation/id :user/id]) :actual] :present)
      (update-todo event)))

(defmethod projection :congregation.event/gis-user-deleted
  [state event]
  (-> state
      (assoc-in [::gis-users (select-keys event [:congregation/id :user/id]) :actual] :absent)
      (update-todo event)))


(def ^:private system (str (ns-name *ns*)))

(defn sort-users [gis-users]
  (sort-by (juxt :congregation/id :user/id) gis-users))

(defn generate-commands [state {:keys [now]}]
  (concat
   (for [gis-user (sort-users (::gis-users-to-be-created state))]
     {:command/type :gis-user.command/create-gis-user
      :command/time (now)
      :command/system system
      :congregation/id (:congregation/id gis-user)
      :user/id (:user/id gis-user)})
   (for [gis-user (sort-users (::gis-users-to-be-deleted state))]
     {:command/type :gis-user.command/delete-gis-user
      :command/time (now)
      :command/system system
      :congregation/id (:congregation/id gis-user)
      :user/id (:user/id gis-user)})))
