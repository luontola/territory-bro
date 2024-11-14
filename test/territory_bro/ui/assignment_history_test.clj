(ns territory-bro.ui.assignment-history-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [territory-bro.test.fixtures :refer :all]
            [territory-bro.ui.assignment-history :as assignment-history]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.territory-page-test :as territory-page-test])
  (:import (java.time LocalDate)
           (java.util UUID)))

(def assignment-id (UUID. 0 1))
(def publisher-id (UUID. 0 2))
(def start-date (LocalDate/of 2000 1 1))
(def end-date (LocalDate/of 2000 2 1))
(def today (LocalDate/of 2000 3 1))

(def assigned-assignment
  {:assignment/id assignment-id
   :assignment/start-date start-date
   :publisher/id publisher-id
   :publisher/name "John Doe"})
(def covered-assignment
  (assoc assigned-assignment :assignment/covered-dates #{end-date}))
(def returned-assignment
  (assoc assigned-assignment :assignment/end-date end-date))
(def covered-and-returned-assignment
  (assoc covered-assignment :assignment/end-date end-date))

(deftest assignment->events-test
  (is (= [] (assignment-history/assignment->events nil)))

  (testing "assigned"
    (is (= [{:type :event
             :date start-date
             :assigned? true
             :publisher/name "John Doe"}]
           (assignment-history/assignment->events assigned-assignment))))

  (testing "covered & returned same day"
    (is (= [{:type :event
             :date start-date
             :assigned? true
             :publisher/name "John Doe"}
            {:type :event
             :date end-date
             :returned? true
             :covered? true}]
           (assignment-history/assignment->events covered-and-returned-assignment)))))

(deftest interpose-durations-within-assignment-test
  (let [t1 (LocalDate/of 2000 1 1)
        t2 (.plusMonths t1 1)
        today (.plusMonths t1 2)]

    (testing "ongoing assignment: calculates the duration until today"
      (is (= [{:type :event, :date t1, :assigned? true}
              {:type :duration, :months 2, :status :assigned}]
             (assignment-history/interpose-durations-within-assignment today [{:type :event, :date t1, :assigned? true}]))))

    (testing "completed assignment: calculates the duration between events"
      (is (= [{:type :event, :date t1, :assigned? true}
              {:type :duration, :months 1, :status :assigned}
              {:type :event, :date t2, :returned? true}]
             (assignment-history/interpose-durations-within-assignment today [{:type :event, :date t1, :assigned? true}
                                                                              {:type :event, :date t2, :returned? true}]))))

    (testing "doesn't add duration if the dates are the same"
      (is (= [{:type :event, :date t1, :assigned? true}
              {:type :event, :date t1, :returned? true}]
             (assignment-history/interpose-durations-within-assignment today [{:type :event, :date t1, :assigned? true}
                                                                              {:type :event, :date t1, :returned? true}]))))))

(deftest interpose-durations-between-assignments-test
  (let [t1 (LocalDate/of 2000 1 1)
        t2 (.plusMonths t1 1)
        t3 (.plusMonths t1 2)
        today (.plusMonths t1 3)]

    (testing "no assignments: no change"
      (is (= [] (assignment-history/interpose-durations-between-assignments today []))))

    (testing "ongoing assignment: no change"
      (is (= [{:type :assignment, :assignment/start-date t1, :assignment/end-date nil}]
             (assignment-history/interpose-durations-between-assignments
              today [{:type :assignment, :assignment/start-date t1, :assignment/end-date nil}]))))

    (testing "completed assignment: calculates the duration after assignment until today"
      (is (= [{:type :assignment, :assignment/start-date t1, :assignment/end-date t2}
              {:type :duration, :months 2, :status :vacant}]
             (assignment-history/interpose-durations-between-assignments
              today [{:type :assignment, :assignment/start-date t1, :assignment/end-date t2}]))))

    (testing "multiple assignments: calculates the durations between assignments"
      (is (= [{:type :assignment, :assignment/start-date t1, :assignment/end-date t2}
              {:type :duration, :months 1, :status :vacant}
              {:type :assignment, :assignment/start-date t3, :assignment/end-date nil}]
             (assignment-history/interpose-durations-between-assignments
              today [{:type :assignment, :assignment/start-date t1, :assignment/end-date t2}
                     {:type :assignment, :assignment/start-date t3, :assignment/end-date nil}]))))

    (testing "doesn't add duration if the dates are the same"
      ;; This rule makes it simple to add today's date after all assignments,
      ;; without checking whether it already exists at the end of an ongoing assignment.
      ;; This may also be relevant if a territory is returned and assigned to another
      ;; publisher during the same day.
      (is (= [{:type :assignment, :assignment/start-date t1, :assignment/end-date t2}
              {:type :assignment, :assignment/start-date t2, :assignment/end-date today}]
             (assignment-history/interpose-durations-between-assignments
              today [{:type :assignment, :assignment/start-date t1, :assignment/end-date t2}
                     {:type :assignment, :assignment/start-date t2, :assignment/end-date today}])))
      ;; The same use case, but instead of the being recorded as returned and reassigned
      ;; during the same day, it was recorded as reassigned the next day.
      (is (= [{:type :assignment, :assignment/start-date t1, :assignment/end-date t2}
              {:type :assignment
               :assignment/start-date (.plusDays t2 1)
               :assignment/end-date (.minusDays today 1)}]
             (assignment-history/interpose-durations-between-assignments
              today [{:type :assignment, :assignment/start-date t1, :assignment/end-date t2}
                     {:type :assignment :assignment/start-date (.plusDays t2 1) :assignment/end-date (.minusDays today 1)}]))
          "or the next day"))

    (testing "gives a warning if the dates are not in ascending order"
      ;; This can happen if the user inputs historical assignments and makes a mistake,
      ;; so that some assignments are overlapping.
      (is (= [{:type :assignment, :assignment/start-date t1, :assignment/end-date t3}
              {:type :duration, :months -1, :status :vacant, :temporal-paradox? true}
              {:type :assignment, :assignment/start-date t2, :assignment/end-date nil}]
             (assignment-history/interpose-durations-between-assignments
              today [{:type :assignment, :assignment/start-date t1, :assignment/end-date t3}
                     {:type :assignment, :assignment/start-date t2, :assignment/end-date nil}]))))))

(deftest compile-assignment-history-rows-test
  (testing "no assignments"
    (is (empty? (assignment-history/compile-assignment-history-rows [] today))))

  (testing "assigned"
    (is (= [{:type :assignment
             :assignment/id assignment-id
             :assignment/start-date start-date
             :assignment/end-date nil
             :rows [{:type :event
                     :date start-date
                     :assigned? true
                     :publisher/name "John Doe"}
                    {:type :duration
                     :status :assigned
                     :months 2}]}]
           (assignment-history/compile-assignment-history-rows [assigned-assignment] today))))

  (testing "covered"
    (is (= [{:type :assignment
             :assignment/id assignment-id
             :assignment/start-date start-date
             :assignment/end-date nil
             :rows [{:type :event
                     :date start-date
                     :assigned? true
                     :publisher/name "John Doe"}
                    {:type :duration
                     :status :assigned
                     :months 1}
                    {:type :event
                     :date end-date
                     :covered? true}
                    {:type :duration
                     :status :assigned
                     :months 1}]}]
           (assignment-history/compile-assignment-history-rows [covered-assignment] today))))

  (testing "returned"
    (is (= [{:type :assignment
             :assignment/id assignment-id
             :assignment/start-date start-date
             :assignment/end-date end-date
             :rows [{:type :event
                     :date start-date
                     :assigned? true
                     :publisher/name "John Doe"}
                    {:type :duration
                     :status :assigned
                     :months 1}
                    {:type :event
                     :date end-date
                     :returned? true}]}
            {:type :duration
             :status :vacant
             :months 1}]
           (assignment-history/compile-assignment-history-rows [returned-assignment] today)))))

(deftest view-assignment-test
  (let [model territory-page-test/editing-assignment-model]

    (testing "shows the assignment"
      (is (= (html/normalize-whitespace
              "                                   Edit
               2000-02-01   📥 Returned
                            ✅ Covered
               1 months
               2000-01-01   ⤴️ Assigned to John Doe")
             (-> (assignment-history/view-assignment model)
                 html/visible-text))))

    (testing "shows a placeholder for deleted assignments"
      (is (= "The territory assignment has been deleted"
             (-> (assignment-history/view-assignment (dissoc model :assignment))
                 html/visible-text))))))

(deftest edit-assignment-test
  (is (= (html/normalize-whitespace
          "2000-02-01   📥 Returned
                        ✅ Covered
           1 months
           2000-01-01   ⤴️ Assigned to John Doe
           Delete  Cancel")
         (-> (assignment-history/edit-assignment territory-page-test/editing-assignment-model)
             html/visible-text))))

(deftest view-test
  (let [today (LocalDate/of 2024 10 29)
        t1 (-> today (.minusMonths 16) (.minusDays 30))
        t2 (-> today (.minusMonths 14) (.minusDays 20))
        t3 (-> today (.minusMonths 6) (.minusDays 16))
        t4 (-> today (.minusMonths 2) (.minusDays 4))]

    (testing "shows the assignment history on a timeline"
      (let [model {:assignment-history (shuffle [{:assignment/id (random-uuid)
                                                  :assignment/start-date t1
                                                  ;; Typical use case: returned and marked as covered at the same time.
                                                  :assignment/covered-dates #{t2}
                                                  :assignment/end-date t2
                                                  :publisher/id (random-uuid)
                                                  :publisher/name "Joe Blow"}
                                                 {:assignment/id (random-uuid)
                                                  :assignment/start-date t3
                                                  ;; CO's visit week: marked as covered, but continuing to hold the territory.
                                                  :assignment/covered-dates #{t4}
                                                  :publisher/id (random-uuid)
                                                  :publisher/name "John Doe"}])
                   :today today}]
        (is (= (html/normalize-whitespace
                "                                    Edit
                 2 months
                 2024-08-25    ✅ Covered
                 4 months
                 2024-04-13    ⤴️ Assigned to John Doe
                 8 months
                                                     Edit
                 2023-08-09    📥 Returned
                               ✅ Covered
                 2 months
                 2023-05-30    ⤴️ Assigned to Joe Blow")
               (-> (assignment-history/view model)
                   html/visible-text)))))

    (testing "when start dates are the same, sorts by end date, and vice versa"
      ;; This situation happens if a territory is first assigned to one publisher,
      ;; but right after that the decision was changed, and it was returned and
      ;; assigned to another publisher. Though the start date is the same, logically
      ;; the second assignment is newer.
      (let [model {:assignment-history (shuffle [{:assignment/id (random-uuid)
                                                  ;; Territory wasn't visited: returned without marking as covered.
                                                  :assignment/start-date t1
                                                  :assignment/end-date t1
                                                  :publisher/id (random-uuid)
                                                  :publisher/name "Publisher 1"}
                                                 {:assignment/id (random-uuid)
                                                  :assignment/start-date t1
                                                  :assignment/end-date t2
                                                  :publisher/id (random-uuid)
                                                  :publisher/name "Publisher 2"}
                                                 {:assignment/id (random-uuid)
                                                  :assignment/start-date t2
                                                  :assignment/end-date t2
                                                  :publisher/id (random-uuid)
                                                  :publisher/name "Publisher 3"}])
                   :today today}]
        (is (= (html/normalize-whitespace
                "14 months
                                                  Edit
                 2023-08-09 📥 Returned
                            ⤴️ Assigned to Publisher 3
                                                  Edit
                 2023-08-09 📥 Returned
                 2 months
                 2023-05-30 ⤴️ Assigned to Publisher 2
                                                  Edit
                 2023-05-30 📥 Returned
                            ⤴️ Assigned to Publisher 1")
               (-> (assignment-history/view model)
                   html/visible-text)))))

    (testing "warns about temporal paradoxes"
      ;; If the user enters historical assignments and makes a mistake with
      ;; one of the dates, it's possible to enter overlapping assignments.
      ;; Show a warning about them, so that the user will hopefully notice
      ;; it and correct the mistake.
      (let [model {:assignment-history (shuffle [{:assignment/id (random-uuid)
                                                  :assignment/start-date t1
                                                  :assignment/end-date t3
                                                  :publisher/id (random-uuid)
                                                  :publisher/name "Publisher 1"}
                                                 {:assignment/id (random-uuid)
                                                  :assignment/start-date t2
                                                  :assignment/end-date t4
                                                  :publisher/id (random-uuid)
                                                  :publisher/name "Publisher 2"}])
                   :today today}]
        (is (str/includes?
             (-> (assignment-history/view model)
                 html/visible-text)
             (html/normalize-whitespace
              "2023-08-09    ⤴️ Assigned to Publisher 2
               ⚠️
                                                  Edit
               2024-04-13    📥 Returned")))))

    (testing "ongoing assignments are always first in the list, even if due to a temporal paradox they are older"
      (let [model {:assignment-history (shuffle [{:assignment/id (random-uuid)
                                                  :assignment/start-date t3
                                                  :assignment/end-date t4
                                                  :publisher/id (random-uuid)
                                                  :publisher/name "Publisher 1"}
                                                 {:assignment/id (random-uuid)
                                                  :assignment/start-date t1
                                                  :publisher/id (random-uuid)
                                                  :publisher/name "Publisher 2"}])
                   :today today}]
        (is (= (html/normalize-whitespace
                "                                 Edit
                 16 months
                 2023-05-30 ⤴️ Assigned to Publisher 2
                 ⚠️
                                                  Edit
                 2024-08-25 📥 Returned
                 4 months
                 2024-04-13 ⤴️ Assigned to Publisher 1")
               (-> (assignment-history/view model)
                   html/visible-text)))))

    (testing "placeholder for deleted publishers"
      (let [model {:assignment-history [{:assignment/id (random-uuid)
                                         :assignment/start-date t1
                                         :publisher/id (random-uuid)
                                         :publisher/name nil}]
                   :today today}]
        (is (= (html/normalize-whitespace
                "                                 Edit
                 16 months
                 2023-05-30 ⤴️ Assigned to [deleted]")
               (-> (assignment-history/view model)
                   html/visible-text)))))))
