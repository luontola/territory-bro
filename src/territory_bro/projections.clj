(ns territory-bro.projections
  (:require [clojure.tools.logging :as log]
            [mount.core :as mount]
            [territory-bro.dispatcher :as dispatcher]
            [territory-bro.domain.card-minimap-viewport :as card-minimap-viewport]
            [territory-bro.domain.congregation :as congregation]
            [territory-bro.domain.congregation-boundary :as congregation-boundary]
            [territory-bro.domain.conversion-funnel :as conversion-funnel]
            [territory-bro.domain.demo :as demo]
            [territory-bro.domain.region :as region]
            [territory-bro.domain.share :as share]
            [territory-bro.domain.territory :as territory]
            [territory-bro.gis.db-admin :as db-admin]
            [territory-bro.gis.gis-change :as gis-change]
            [territory-bro.gis.gis-db :as gis-db]
            [territory-bro.gis.gis-user :as gis-user]
            [territory-bro.gis.gis-user-process :as gis-user-process]
            [territory-bro.infra.config :as config]
            [territory-bro.infra.db :as db]
            [territory-bro.infra.event-store :as event-store]
            [territory-bro.infra.executors :as executors]
            [territory-bro.infra.poller :as poller])
  (:import (com.google.common.util.concurrent ThreadFactoryBuilder)
           (java.time Duration)
           (java.util.concurrent Executors ScheduledExecutorService TimeUnit)
           (org.openjdk.jol.info GraphLayout)))

;;;; Cache

(mount/defstate *cache
  :start (atom {:last-event nil
                :state nil}))

(defn projection [state event]
  (try
    (-> state
        (card-minimap-viewport/projection event)
        (congregation-boundary/projection event)
        (congregation/projection event)
        (db-admin/projection event)
        (gis-change/projection event)
        (gis-user-process/projection event)
        (gis-user/projection event)
        (region/projection event)
        (share/projection event)
        (territory/projection event)
        ;; last, so it can read the state produces by the other projections
        (conversion-funnel/projection event))
    (catch Throwable t
      (log/error t "Failed to process event" (pr-str event))
      (throw t))))

(defn- apply-transient-events [cache events]
  (update cache :state #(reduce projection % events)))

(defn- apply-new-events [cache conn]
  (let [new-events (event-store/read-all-events conn {:since (:event/global-revision (:last-event cache))})]
    (reduce (fn [cache event]
              (-> cache
                  (update :state projection event)
                  (assoc :last-event event)))
            cache
            new-events)))

(defn cached-state []
  (:state @*cache))

(defn current-state
  "Calculates the current state from all events, including uncommitted ones,
   but does not update the cache (it could cause dirty reads to others)."
  [conn]
  (:state (apply-new-events @*cache conn)))


(defn memory-usage ^GraphLayout [obj]
  ;; This will print the following warnings. The size estimates might be a bit off,
  ;; but that is fine as long as they're in the same ballpark.
  ;;
  ;; # WARNING: Unable to get Instrumentation. Dynamic Attach failed. You may add this JAR as -javaagent manually,
  ;; or supply -Djdk.attach.allowAttachSelf
  ;; # WARNING: Unable to attach Serviceability Agent. You can try again with escalated privileges. Two options:
  ;; a) use -Djol.tryWithSudo=true to try with sudo; b) echo 0 | sudo tee /proc/sys/kernel/yama/ptrace_scope
  ;;
  ;; To enable the Java Agent, you will need to add the following manifest entries
  ;; to the uberjar in project.clj. See https://github.com/openjdk/jol#use-as-library-dependency
  (comment
    :manifest {"Premain-Class" "org.openjdk.jol.vm.InstrumentationSupport"
               "Launcher-Agent-Class" "org.openjdk.jol.vm.InstrumentationSupport$Installer"})
  ;;
  ;; With those manifest entries in place, the reported size changes by about 5 %. No significant difference.
  ;; But even then, JOL will give the following warning.
  ;;
  ;; # WARNING: Unable to attach Serviceability Agent. Unable to attach even with module exceptions:
  ;; [org.openjdk.jol.vm.sa.SASupportException: Sense failed., org.openjdk.jol.vm.sa.SASupportException: Sense failed.,
  ;; org.openjdk.jol.vm.sa.SASupportException: Sense failed.]
  ;;
  ;; The javadocs of org.openjdk.jol.vm.sa.ServiceabilityAgentSupport indicate that
  ;; it might require running the app as root, which is not worth the risks.
  (GraphLayout/parseInstance (into-array [obj])))

(defn memory-usage-by-key [m]
  (doseq [entry (->> m
                     (map (fn [[k v]]
                            {:key k
                             :size (if (nil? v)
                                     0
                                     (.totalSize (memory-usage v)))}))
                     (sort-by :size)
                     reverse)]
    (printf "%,12d bytes in %s\n" (:size entry) (:key entry))))

(defn congregation-state [state cong-id]
  (update-vals state #(get % cong-id)))

(comment
  (memory-usage-by-key (cached-state))
  (memory-usage-by-key (::territory/territories (cached-state)))
  (memory-usage-by-key (congregation-state (cached-state) "demo")))


;;;; Refreshing projections

(defn- refresh-projections! []
  (let [[old new] (swap-vals! *cache (fn [cached]
                                       ;; Though this reads the database and is thus a slow
                                       ;; operation, retries on updating the atom should not
                                       ;; happen because it's called from a single thread.
                                       (db/with-transaction [conn {:read-only true}]
                                         (apply-new-events cached conn))))]
    (when-not (identical? old new)
      (log/info "Updated from revision" (:event/global-revision (:last-event old))
                "to" (:event/global-revision (:last-event new))))))

(defn- run-process-managers! [state]
  (db/with-transaction [conn {}]
    (let [commands (concat
                    (gis-user-process/generate-commands state)
                    (db-admin/generate-commands state))
          new-events (->> commands
                          (mapcat (fn [command]
                                    (dispatcher/command! conn state command)))
                          (doall))]
      (seq new-events))))

(defn- update-with-transient-events! [new-events]
  (when-some [transient-events (seq (filter :event/transient? new-events))]
    (swap! *cache apply-transient-events transient-events)
    (log/info "Updated with" (count transient-events) "transient events")))

(defn- refresh-process-managers! []
  (when-some [new-events (run-process-managers! (cached-state))]
    (update-with-transient-events! new-events)
    ;; Some process managers produce persisted events which in turn
    ;; trigger other process managers (e.g. :congregation.event/gis-user-created),
    ;; so we have to refresh the projections and re-run the process managers
    ;; until there are no more commands to execute
    (refresh-projections!)
    (recur)))

(defn- startup-optimizations []
  (db/with-transaction [conn {:read-only true}]
    (let [state (cached-state)
          injections {:get-present-schemas (fn []
                                             (gis-db/get-present-schemas conn {:schema-prefix ""}))
                      :get-present-users (fn []
                                           (gis-db/get-present-users conn {:username-prefix ""
                                                                           :schema-prefix ""}))}]
      (concat
       (db-admin/init-present-schemas state injections)
       (db-admin/init-present-users state injections)))))

(defn- refresh-startup-optimizations! []
  (let [new-events (startup-optimizations)]
    (update-with-transient-events! new-events)))

(defn- demo-gis-events [conn source-cong-id]
  (->> (event-store/read-all-events conn {:congregation source-cong-id})
       (eduction demo/transform-gis-events)))

(defn apply-demo-events [cache]
  (if-some [source-cong-id (:demo-congregation config/env)]
    (db/with-transaction [conn {:read-only true}]
      (let [cache (apply-transient-events cache [demo/congregation-created])
            cache (apply-transient-events cache (demo-gis-events conn source-cong-id))
            congregation (congregation/get-unrestricted-congregation (:state cache) demo/cong-id)
            today (.toLocalDate (congregation/local-time congregation))
            territory-ids (->> (territory/list-unrestricted-territories (:state cache) demo/cong-id)
                               (mapv :territory/id))
            assignment-events (mapcat #(demo/generate-assignment-events % today)
                                      territory-ids)
            cache (apply-transient-events cache assignment-events)]
        cache))
    cache))

(defn generate-demo-data! []
  (log/info "Generating demo data")
  (swap! *cache apply-demo-events))

(defn refresh! []
  (let [startup? (nil? (cached-state))]
    (log/info "Refreshing projections")
    (refresh-projections!)
    (when startup?
      (log/info "Using startup optimizations")
      (refresh-startup-optimizations!)
      (generate-demo-data!))
    (refresh-process-managers!)))

(mount/defstate refresher
  :start (poller/create refresh!)
  :stop (poller/shutdown! refresher))

(defn refresh-async! []
  (poller/trigger! refresher))

(defn await-refreshed [^Duration duration]
  (poller/await refresher duration))


(defonce ^:private scheduled-refresh-thread-factory
  (-> (ThreadFactoryBuilder.)
      (.setNameFormat "territory-bro.projections/scheduled-refresh-%d")
      (.setDaemon true)
      (.setUncaughtExceptionHandler executors/uncaught-exception-handler)
      (.build)))

(mount/defstate scheduled-refresh
  :start (doto (Executors/newScheduledThreadPool 1 scheduled-refresh-thread-factory)
           (.scheduleWithFixedDelay (executors/safe-task refresh-async!)
                                    0 1 TimeUnit/MINUTES))
  :stop (.shutdown ^ScheduledExecutorService scheduled-refresh))


(comment
  (count (:state @*cache))
  (refresh-async!)
  (refresh!))
