;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.db-admin)

(defmulti projection (fn [_state event] (:event/type event)))

(defmethod projection :default
  [state _event]
  state)

(defmethod projection :congregation.event/congregation-created
  [state event]
  (update state ::tenant-schemas-to-create (fnil conj #{}) (select-keys event [:congregation/schema-name])))
