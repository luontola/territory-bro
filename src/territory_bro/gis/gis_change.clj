;; Copyright © 2015-2022 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.gis.gis-change)

(defmulti projection (fn [_state event] (:event/type event)))
(defmethod projection :default [state _event] state)

(defmethod projection :congregation.event/congregation-created
  [state event]
  (assoc-in state [::schema->cong-id (:congregation/schema-name event)] (:congregation/id event)))

(defmethod projection :congregation.event/gis-user-created
  [state event]
  (assoc-in state [::username->user-id (:gis-user/username event)] (:user/id event)))


(def ^:private system (str (ns-name *ns*)))

(defn normalize-change [change]
  (if (and (= :UPDATE (:gis-change/op change))
           (not= (:id (:gis-change/old change))
                 (:id (:gis-change/new change))))
    [(assoc change
            :gis-change/op :DELETE
            :gis-change/new nil)
     (assoc change
            :gis-change/op :INSERT
            :gis-change/old nil)]
    [change]))

(defn change->command [change state]
  (let [{change-id :gis-change/id, :gis-change/keys [schema table user time op old new]} change
        cong-id (get-in state [::schema->cong-id schema])
        user-id (get-in state [::username->user-id user])
        base-command (cond-> {:command/system system
                              :command/time time
                              :gis-change/id change-id
                              :congregation/id cong-id}
                       (some? user-id) (assoc :command/user user-id))]
    ;; TODO: add {:gis-change/id id} to all commands and events for traceability?
    ;; TODO: check for UPDATE with location MULTIPOLYGON EMPTY, i.e. user tried to delete the feature incorrectly
    (case table
      "territory"
      (-> base-command
          (assoc :command/type (case op
                                 :INSERT :territory.command/define-territory
                                 :UPDATE :territory.command/update-territory
                                 :DELETE :territory.command/delete-territory))
          (cond->
            (some? old) (assoc :territory/id (:id old))
            (some? new) (assoc :territory/id (:id new)
                               :territory/number (:number new)
                               :territory/addresses (:addresses new)
                               :territory/region (:subregion new)
                               :territory/meta (:meta new)
                               :territory/location (:location new))))

      "subregion"
      (-> base-command
          (assoc :command/type (case op
                                 :INSERT :region.command/define-region
                                 :UPDATE :region.command/update-region
                                 :DELETE :region.command/delete-region))
          (cond->
            (some? old) (assoc :region/id (:id old))
            (some? new) (assoc :region/id (:id new)
                               :region/name (:name new)
                               :region/location (:location new))))

      "congregation_boundary"
      (-> base-command
          (assoc :command/type (case op
                                 :INSERT :congregation-boundary.command/define-congregation-boundary
                                 :UPDATE :congregation-boundary.command/update-congregation-boundary
                                 :DELETE :congregation-boundary.command/delete-congregation-boundary))
          (cond->
            (some? old) (assoc :congregation-boundary/id (:id old))
            (some? new) (assoc :congregation-boundary/id (:id new)
                               :congregation-boundary/location (:location new))))

      "card_minimap_viewport"
      (-> base-command
          (assoc :command/type (case op
                                 :INSERT :card-minimap-viewport.command/define-card-minimap-viewport
                                 :UPDATE :card-minimap-viewport.command/update-card-minimap-viewport
                                 :DELETE :card-minimap-viewport.command/delete-card-minimap-viewport))
          (cond->
            (some? old) (assoc :card-minimap-viewport/id (:id old))
            (some? new) (assoc :card-minimap-viewport/id (:id new)
                               :card-minimap-viewport/location (:location new)))))))

(defn event-metadata [command]
  (select-keys command [:gis-change/id]))
