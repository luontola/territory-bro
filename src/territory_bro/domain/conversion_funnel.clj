(ns territory-bro.domain.conversion-funnel
  (:require [territory-bro.domain.share :as share]
            [territory-bro.domain.territory :as territory]))

;;;; Read model

(defmulti projection (fn [_state event]
                       (:event/type event)))

(defmethod projection :default [state _event]
  state)

(defn- record-milestone
  ([state event milestone]
   (record-milestone state event milestone nil))
  ([state event milestone condition]
   (if (some? (get-in state [::milestones (:congregation/id event) milestone]))
     state
     (if (or (nil? condition)
             (condition))
       (assoc-in state [::milestones (:congregation/id event) milestone] (:event/time event))
       state))))

(defmethod projection :congregation.event/congregation-created
  [state event]
  (record-milestone state event :congregation-created))

(defmethod projection :congregation-boundary.event/congregation-boundary-defined
  [state event]
  (record-milestone state event :congregation-boundary-created))

(defmethod projection :region.event/region-defined
  [state event]
  (record-milestone state event :region-created))

(defmethod projection :territory.event/territory-defined
  [state event]
  (-> state
      (record-milestone event :territory-created)
      (record-milestone event :ten-territories-created
                        #(<= 10 (count (territory/list-unrestricted-territories state (:congregation/id event)))))))

(defmethod projection :territory.event/territory-assigned
  [state event]
  (record-milestone state event :territory-assigned))

(defmethod projection :share.event/share-created
  [state event]
  (record-milestone state event :share-link-created #(= :link (:share/type event))))

(defmethod projection :share.event/share-opened
  [state event]
  (let [share (share/get-share state (:share/id event))
        event (assoc event :congregation/id (:congregation/id share))]
    (record-milestone state event :qr-code-scanned #(= :qr-code (:share/type share)))))


;;;; Queries

(defn milestones [state cong-id]
  (get-in state [::milestones cong-id]))
