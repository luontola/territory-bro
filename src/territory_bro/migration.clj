;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.migration
  (:require [clojure.tools.logging :as log]
            [territory-bro.config :as config]
            [territory-bro.congregation :as congregation]
            [territory-bro.db :as db]
            [territory-bro.events :as events]
            [territory-bro.gis-user :as gis-user]
            [territory-bro.region :as region]
            [territory-bro.territory :as territory]
            [territory-bro.user :as user]))

(defn migrate-congregation [tenant]
  (db/as-tenant tenant
    (let [regions (db/query :find-regions)
          territories (db/query :find-territories)
          admins (get-in config/env [:tenant tenant :admins])]
      (db/with-db [conn {}] ; to be able to see the new schema
        (let [cong-id (congregation/create-congregation! conn (.toUpperCase (name tenant)))]

          (doseq [admin admins]
            (let [user-id (user/save-user! conn admin {})]
              (congregation/grant-access! conn cong-id user-id)
              (gis-user/create-gis-user! conn cong-id user-id)))

          (congregation/use-schema conn cong-id)

          (doseq [region regions]
            (cond
              (:congregation region) (region/create-congregation-boundary! conn (:location region))
              (:subregion region) (region/create-subregion! conn (:name region) (:location region))
              (:minimap_viewport region) (region/create-card-minimap-viewport! conn (:location region))
              :else (region/create-subregion! conn (:name region) (:location region))))

          (doseq [territory territories]
            (let [meta (dissoc territory :id :number :address :region :location :location_2)]
              (territory/create-territory! conn {:territory/number (:number territory)
                                                 :territory/addresses (:address territory)
                                                 :territory/subregion (:region territory)
                                                 :territory/meta meta
                                                 :territory/location (:location territory)})))))))
  (log/info "Migrated tenant" tenant))

(comment
  (doseq [tenant (keys (:tenant config/env))]
    (binding [events/*current-system* "migration"]
      (migrate-congregation tenant))))
