;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.migration
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [clojure.tools.logging :as log]
            [territory-bro.config :as config]
            [territory-bro.congregation :as congregation]
            [territory-bro.db :as db]
            [territory-bro.events :as events]
            [territory-bro.gis-user :as gis-user]
            [territory-bro.region :as region]
            [territory-bro.territory :as territory]
            [territory-bro.user :as user])
  (:import (java.time Instant OffsetDateTime ZoneOffset ZoneId ZonedDateTime)
           (java.time.format DateTimeFormatter DateTimeParseException)
           (java.awt Toolkit)
           (java.awt.datatransfer DataFlavor StringSelection)))

(def patterns [(DateTimeFormatter/ofPattern "EE LLL d y H:m:s 'GMT'Z")
               (-> (DateTimeFormatter/ofPattern "d LLL H:m:s y")
                   (.withZone (ZoneId/of "Europe/Helsinki")))
               (DateTimeFormatter/ofPattern "EE, d LLL y H:m:s Z")
               (DateTimeFormatter/ofPattern "EE LLL d H:m:s y Z")])

(defn- parse-instant [s]
  (let [parsed (->> patterns
                    (map (fn [pattern]
                           (try
                             (-> s
                                 (ZonedDateTime/parse pattern)
                                 (.toInstant))
                             (catch DateTimeParseException e
                               (str e "\n\t for pattern: " pattern))))))]
    (or (->> parsed
             (filter (partial instance? Instant))
             first)
        (throw (RuntimeException. (str "Could not parse '" s "'\n" (str/join "\n" parsed)))))))

(deftest parse-instant-test
  (is (= (.toInstant (OffsetDateTime/of 2015 12 27
                                        19 32 0 0
                                        (ZoneOffset/ofHours 2)))
         (parse-instant "Sun Dec 27 2015 19:32:00 GMT+0200")))
  (is (= (.toInstant (OffsetDateTime/of 2015 11 7
                                        22 44 53 0
                                        (ZoneOffset/ofHours 2)))
         (parse-instant "7 Nov 22:44:53 2015")))
  (is (= (.toInstant (OffsetDateTime/of 2016 2 19
                                        18 40 18 0
                                        (ZoneOffset/ofHours 2)))
         (parse-instant "Fri, 19 Feb 2016 18:40:18 +0200")))
  (is (= (.toInstant (OffsetDateTime/of 2018 11 27
                                        15 48 57 0
                                        (ZoneOffset/ofHours 2)))
         (parse-instant "Tue Nov 27 15:48:57 2018 +0200"))))

(defn migrate-congregation [tenant]
  (db/as-tenant tenant
    (let [regions (db/query :find-regions)
          territories (db/query :find-territories)
          admins (get-in config/env [:tenant tenant :admins])
          created (Instant/parse (get-in config/env [:tenant tenant :created]))]

      (binding [events/*current-time* created]
        (db/with-db [conn {}]
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
                                                   :territory/location (:location territory)}))))))))
  (log/info "Migrated tenant" tenant))

(comment
  (let [cp (-> (Toolkit/getDefaultToolkit)
               (.getSystemClipboard))
        in (.getData cp DataFlavor/stringFlavor)
        time (parse-instant in)
        out (.toString time)
        contents (StringSelection. out)]
    (.setContents cp contents contents)
    [in out])

  (let [tenants (keys (:tenant config/env))]
    (doseq [[i tenant] (map-indexed vector tenants)]
      (log/info (str "Start migrating tenant " tenant " (" (inc i) "/" (count tenants) ")"))
      (binding [events/*current-system* "migration"]
        (migrate-congregation tenant)))
    (log/info "All done")))
