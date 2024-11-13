(ns territory-bro.ui.assignment-history
  (:require [clojure.string :as str]
            [medley.core :refer [assoc-some]]
            [territory-bro.infra.util :as util]
            [territory-bro.ui.assignment :as assignment]
            [territory-bro.ui.css :as css]
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

(defn- duration-entry [status ^LocalDate date-1 ^LocalDate date-2]
  (when (and (some? date-1)
             (some? date-2)
             (not= date-1 date-2)
             (not= date-1 (.minusDays date-2 1)))
    (-> {:type :duration
         :status status
         :months (util/months-difference date-1 date-2)}
        (assoc-some :temporal-paradox? (when (. date-2 isBefore date-1)
                                         true)))))

(defn interpose-durations-within-assignment [today events]
  (let [completed-assignment? (some :returned? events)]
    (->> (concat events [(when-not completed-assignment?
                           {:date today})])
         (partition 2 1)
         (mapcat (fn [[event-1 event-2]]
                   [event-1
                    (duration-entry :assigned (:date event-1) (:date event-2))]))
         (filter some?))))

(defn interpose-durations-between-assignments [today assignments]
  (->> (concat assignments [{:assignment/start-date today}])
       (partition 2 1)
       (mapcat (fn [[assignment-1 assignment-2]]
                 [assignment-1
                  (duration-entry :vacant (:assignment/end-date assignment-1) (:assignment/start-date assignment-2))]))
       (remove nil?)))

(defn compile-assignment-history-rows [assignment-history today]
  (->> assignment-history
       (sort-by (juxt (comp nil? :assignment/end-date)
                      :assignment/start-date
                      :assignment/end-date))
       (map (fn [assignment]
              {:type :assignment
               :assignment/id (:assignment/id assignment)
               :assignment/start-date (:assignment/start-date assignment)
               :assignment/end-date (:assignment/end-date assignment)
               :rows (->> (assignment->events assignment)
                          (interpose-durations-within-assignment today))}))
       (interpose-durations-between-assignments today)))


(defn- grid-row-wrapper [{:keys [grid-row]} content]
  (let [styles (:AssignmentHistory (css/modules))]
    (h/html
     [:div {:class (:row styles)
            :style (when (some? grid-row)
                     {:grid-row grid-row})}
      content])))

(defn- duration-row [row]
  (let [styles (:AssignmentHistory (css/modules))]
    (grid-row-wrapper
     row
     (h/html
      [:div {:class (html/classes (:duration styles)
                                  (when (= :vacant (:status row))
                                    (:vacant styles)))}
       (if (:temporal-paradox? row)
         " ⚠️ "
         (-> (i18n/t "Assignment.durationMonths")
             (str/replace "{{months}}" (str (:months row)))))]))))

(defn- event-row [row]
  (let [styles (:AssignmentHistory (css/modules))]
    (grid-row-wrapper
     row
     (h/html
      [:div {:class (:event-date styles)}
       (:date row)]
      [:div {:class (:event-description styles)}
       (when (:returned? row)
         [:div "📥 " (i18n/t "Assignment.returned")])
       (when (:covered? row)
         [:div "✅ " (i18n/t "Assignment.covered")])
       (when (:assigned? row)
         [:div "⤴️ " (-> (i18n/t "Assignment.assignedToPublisher")
                         (str/replace "{{name}}" (assignment/format-publisher-name row)))])]))))

(defn- assignment-row [{:keys [rows] :as assignment} {:keys [editing?]}]
  (let [styles (:AssignmentHistory (css/modules))
        assignment-url (str html/*page-path* "/assignments/history/" (:assignment/id assignment))]
    (h/html
     [:div {:hx-target "this"
            :hx-swap "outerHTML"
            :class (html/classes (:row styles)
                                 (when editing?
                                   (:edit-mode-assignment styles)))}
      [:div {:class (:timeline styles)
             ;; XXX: workaround to Hiccup style attribute bug https://github.com/weavejester/hiccup/issues/211
             :style (identity {:grid-row (str "1 / span " (count rows))})}]

      (when-not editing?
        [:div {:class (:controls styles)}
         [:button.pure-button {:type "button"
                               :hx-get (str assignment-url "/edit")
                               :class (:edit-button styles)}
          (i18n/t "Assignment.form.edit")]])

      (for [row (->> rows
                     reverse
                     (map-indexed (fn [idx row]
                                    (assoc row :grid-row (inc idx)))))]
        (case (:type row)
          :event (event-row row)
          :duration (duration-row row)))

      (when editing?
        [:div {:class (:edit-mode-controls styles)
               ;; XXX: workaround to Hiccup style attribute bug https://github.com/weavejester/hiccup/issues/211
               :style (identity {:grid-row (inc (count rows))})}
         [:button.pure-button {:type "button"
                               :hx-delete assignment-url
                               :class (:delete-button styles)}
          " 🚧 " ; TODO: not yet implemented
          (i18n/t "Assignment.form.delete")]
         " "
         [:button.pure-button {:type "button"
                               :hx-get assignment-url}
          (i18n/t "Assignment.form.cancel")]])])))


(defn- compile-single-assignment [assignment today]
  (->> (compile-assignment-history-rows [assignment] today)
       (filter #(= :assignment (:type %)))
       first))

(defn view-assignment [{:keys [assignment today]}]
  (if-some [row (compile-single-assignment assignment today)]
    (assignment-row row {})
    ""))

(defn edit-assignment [{:keys [assignment today]}]
  (if-some [row (compile-single-assignment assignment today)]
    (assignment-row row {:editing? true})
    ""))

(defn view [{:keys [assignment-history today]}]
  (if (empty? assignment-history)
    (h/html
     [:div#empty-assignment-history])
    (let [styles (:AssignmentHistory (css/modules))
          rows (compile-assignment-history-rows assignment-history today)]
      (h/html
       [:div {:class (:assignment-history styles)}
        (for [row (reverse rows)]
          (case (:type row)
            :assignment (assignment-row row {})
            :duration (duration-row row)))]))))
