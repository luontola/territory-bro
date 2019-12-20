;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.dispatcher
  (:require [territory-bro.commands :as commands]
            [territory-bro.congregation :as congregation]
            [territory-bro.db-admin :as db-admin]
            [territory-bro.gis-user :as gis-user]))

(defn command! [conn state command]
  (let [command (commands/validate-command command)]
    (case (namespace (:command/type command))
      "congregation.command" (do
                               (congregation/command! conn command state)
                               nil) ; TODO: return produced events?
      "gis-user.command" (do
                           (gis-user/handle-command! conn command state)
                           ;; XXX: refresh! should recur also when persisted events are produced, so maybe return them from the command handler?
                           [{:event/type :fake-event-to-trigger-refresh
                             :event/transient? true}])
      "db-admin.command" (db-admin/handle-command! command state)
      (throw (IllegalArgumentException. (str "Unrecognized command: " (pr-str command)))))))
