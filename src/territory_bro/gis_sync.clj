;; Copyright © 2015-2020 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.gis-sync)

(defmulti projection (fn [_state event] (:event/type event)))
(defmethod projection :default [state _event] state)

(defmethod projection :congregation.event/congregation-created
  [state event]
  (assoc-in state [::schema->cong-id (:congregation/schema-name event)] (:congregation/id event)))

(defmethod projection :congregation.event/gis-user-created
  [state event]
  (assoc-in state [::username->user-id (:gis-user/username event)] (:user/id event)))


(def ^:private system (str (ns-name *ns*)))

(defn- apply-replacement-id [change]
  (let [replacement-id (:replacement_id change)]
    (if (some? replacement-id)
      (cond-> change
        (some? (:old change)) (assoc-in [:old :id] replacement-id)
        (some? (:new change)) (assoc-in [:new :id] replacement-id))
      change)))

(defn change->command [change state]
  (let [{:keys [id schema table user time op old new]} (apply-replacement-id change)
        cong-id (get-in state [::schema->cong-id schema])
        user-id (get-in state [::username->user-id user])
        base-command {:command/system system
                      :command/user user-id ; TODO: omit the key if user is not known
                      :command/time time
                      :congregation/id cong-id}]
    ;; TODO: add {:gis-change/id id} to all commands and events for traceability?
    ;; TODO: check for UPDATE with location MULTIPOLYGON EMPTY, i.e. user tried to delete the feature incorrectly
    (case table
      "territory"
      (-> base-command
          (assoc :command/type (case op
                                 :INSERT :territory.command/create-territory
                                 :UPDATE :territory.command/update-territory
                                 :DELETE :territory.command/delete-territory))
          (cond->
            (some? old) (assoc :territory/id (:id old))
            (some? new) (assoc :territory/id (:id new)
                               :territory/number (:number new)
                               :territory/addresses (:addresses new)
                               :territory/subregion (:subregion new)
                               :territory/meta (:meta new)
                               :territory/location (:location new))))

      "subregion"
      (-> base-command
          (assoc :command/type (case op
                                 :INSERT :subregion.command/create-subregion
                                 :UPDATE :subregion.command/update-subregion
                                 :DELETE :subregion.command/delete-subregion))
          (cond->
            (some? old) (assoc :subregion/id (:id old))
            (some? new) (assoc :subregion/id (:id new)
                               :subregion/name (:name new)
                               :subregion/location (:location new))))

      "congregation_boundary"
      (-> base-command
          (assoc :command/type (case op
                                 :INSERT :congregation-boundary.command/create-congregation-boundary
                                 :UPDATE :congregation-boundary.command/update-congregation-boundary
                                 :DELETE :congregation-boundary.command/delete-congregation-boundary))
          (cond->
            (some? old) (assoc :congregation-boundary/id (:id old))
            (some? new) (assoc :congregation-boundary/id (:id new)
                               :congregation-boundary/location (:location new))))

      "card_minimap_viewport"
      (-> base-command
          (assoc :command/type (case op
                                 :INSERT :card-minimap-viewport.command/create-card-minimap-viewport
                                 :UPDATE :card-minimap-viewport.command/update-card-minimap-viewport
                                 :DELETE :card-minimap-viewport.command/delete-card-minimap-viewport))
          (cond->
            (some? old) (assoc :card-minimap-viewport/id (:id old))
            (some? new) (assoc :card-minimap-viewport/id (:id new)
                               :card-minimap-viewport/location (:location new)))))))
