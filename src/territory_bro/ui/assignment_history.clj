(ns territory-bro.ui.assignment-history
  (:require [clojure.string :as str]
            [medley.core :refer [assoc-some]]
            [territory-bro.infra.config :as config]
            [territory-bro.infra.util :as util]
            [territory-bro.ui.assignment :as assignment]
            [territory-bro.ui.hiccup :as h]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.i18n :as i18n])
  (:import (java.time LocalDate)))

(defn assignment->events [assignment]
  (let [dates (->> (concat [(:assignment/start-date assignment)
                            (:assignment/end-date assignment)]
                           (:assignment/covered-dates assignment))
                   (filter some?)
                   distinct
                   sort)]
    (for [date dates]
      (cond-> {:type :event
               :date date}
        (= date (:assignment/start-date assignment))
        (-> (assoc :assigned? true)
            (assoc :publisher/name (:publisher/name assignment)))

        (contains? (:assignment/covered-dates assignment) date)
        (assoc :covered? true)

        (= date (:assignment/end-date assignment))
        (assoc :returned? true)))))

(defn interpose-durations [events]
  (loop [events events
         ^LocalDate previous-date nil
         previous-status :vacant
         results (transient [])]
    (if-some [event (first events)]
      (let [^LocalDate date (:date event)
            status (cond
                     (:assigned? event) :assigned
                     (:returned? event) :vacant
                     :else previous-status)]
        (recur (rest events)
               (or date previous-date)
               status
               (-> results
                   (cond->
                     (and (some? date)
                          (some? previous-date)
                          (not= date previous-date))
                     (conj! (-> {:type :duration
                                 :status previous-status
                                 :months (util/months-difference previous-date date)}
                                (assoc-some :temporal-paradox? (when (. date isBefore previous-date) true)))))
                   (conj! event))))
      (persistent! results))))

(defn- assign-grid-rows [rows]
  (loop [rows rows
         grid-row 1
         results (transient [])]
    (if-some [row (first rows)]
      (if (= :assignment (:type row))
        (recur (rest rows)
               grid-row ; shares the same row with the end event
               (conj! results (-> row
                                  (assoc :grid-row grid-row)
                                  ;; Count the number of rows until the start of the assignment.
                                  ;; Doesn't count the start event, but counts the type=assignment row,
                                  ;; so they cancel each other out.
                                  (assoc :grid-span (count (take-while #(not (:assigned? %)) rows))))))
        (recur (rest rows)
               (inc grid-row)
               (conj! results (assoc row :grid-row grid-row))))
      (persistent! results))))

(defn compile-assignment-history-rows [assignment-history today]
  (->> assignment-history
       (sort-by (juxt (comp nil? :assignment/end-date)
                      :assignment/start-date
                      :assignment/end-date))
       (mapcat (fn [assignment]
                 (-> (assignment->events assignment)
                     vec
                     (cond->
                       (nil? (:assignment/end-date assignment))
                       (conj {:type :today, :date today}))
                     (conj {:type :assignment}))))
       ((fn [events]
          (concat events [{:type :today, :date today}])))
       interpose-durations
       (remove #(= :today (:type %)))
       reverse
       assign-grid-rows))

(defn view [{:keys [assignment-history today]}]
  (let [rows (compile-assignment-history-rows assignment-history today)]
    (h/html
     [:div {:style {:display "grid"
                    :grid-template-columns "[time-start] min-content [time-end timeline-start] 4px [timeline-end event-start] 1fr [event-end controls-start] min-content [controls-end]"
                    :gap "0.5rem"
                    :width "fit-content"
                    :margin "1rem 0"}}
      (for [{:keys [grid-row grid-span] :as row} rows]
        (case (:type row)
          :assignment
          (h/html
           ;; XXX: workaround to Hiccup style attribute bug https://github.com/weavejester/hiccup/issues/211
           [:div {:style (identity {:grid-column "timeline-start / timeline-end"
                                    :grid-row (str grid-row " / " (+ grid-row grid-span))
                                    :background "linear-gradient(to top, #3330, #333f 1.5rem, #333f calc(100% - 1.5rem), #3330)"})}]
           [:div {:style (identity {:grid-column "controls-start / controls-end"
                                    :grid-row grid-row
                                    :text-align "right"
                                    :margin-left "1em"})}
            ;; TODO: at first add just a checkmark for deleting the assignment? simpler to implement than full editing
            #_[:a {:href "#"
                   :style {:margin-left "1em"}
                   :title "Delete assignment"
                   :aria-label "Delete assignment"}
               (html/inline-svg "icons/close.svg")]
            (if (:dev config/env)
              [:a {:href "#"
                   :onclick "return false"}
               (i18n/t "Assignment.form.edit")]
              [:span {:data-test-icon (i18n/t "Assignment.form.edit")}])])

          :duration
          (h/html
           [:div {:style (identity {:grid-column "time-start / time-end"
                                    :grid-row grid-row
                                    :white-space "nowrap"
                                    :text-align "center"
                                    :padding "0.7rem 0"
                                    :color (when (= :vacant (:status row))
                                             "#999")})}
            (if (:temporal-paradox? row)
              " ⚠️ "
              (-> (i18n/t "Assignment.durationMonths")
                  (str/replace "{{months}}" (str (:months row)))))])

          :event
          (h/html
           [:div {:style (identity {:grid-column "time-start / time-end"
                                    :grid-row grid-row
                                    :white-space "nowrap"})}
            (:date row)]
           [:div {:style (identity {:grid-column "event-start / event-end"
                                    :grid-row grid-row
                                    :display "flex"
                                    :flex-direction "column"
                                    :gap "0.25rem"})}
            (when (:returned? row)
              [:div "📥 " (i18n/t "Assignment.returned")])
            (when (:covered? row)
              [:div "✅ " (i18n/t "Assignment.covered")])
            (when (:assigned? row)
              [:div "⤴️ " (-> (i18n/t "Assignment.assignedToPublisher")
                              (str/replace "{{name}}" (assignment/format-publisher-name row)))])])))])))
