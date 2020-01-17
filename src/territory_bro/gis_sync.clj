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


(defn change->command [change state]
  (let [{:keys [id schema table user time op old new]} change
        cong-id (get-in state [::schema->cong-id schema])
        user-id (get-in state [::username->user-id user])
        base-command {:command/user user-id
                      :command/time time
                      :congregation/id cong-id}]
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
                               :congregation-boundary/location (:location new)))))))
