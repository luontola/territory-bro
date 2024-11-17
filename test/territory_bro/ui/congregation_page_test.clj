(ns territory-bro.ui.congregation-page-test
  (:require [clojure.test :refer :all]
            [matcher-combinators.test :refer :all]
            [territory-bro.domain.congregation :as congregation]
            [territory-bro.domain.demo :as demo]
            [territory-bro.domain.dmz-test :as dmz-test]
            [territory-bro.domain.publisher :as publisher]
            [territory-bro.test.fixtures :refer :all]
            [territory-bro.test.testutil :as testutil :refer [replace-in]]
            [territory-bro.ui.congregation-page :as congregation-page]
            [territory-bro.ui.html :as html])
  (:import (java.time LocalDate)))

(def model
  {:congregation {:congregation/name "Example Congregation"}
   :getting-started {:congregation-boundary? false
                     :territories? false}
   :statistics {:territories 0
                :assigned-territories 0
                :covered-in-past-6-months 0
                :covered-in-past-12-months 0
                :average-assignment-days nil}
   :permissions {:view-printouts-page true
                 :view-settings-page true
                 :view-statistics true
                 :view-getting-started true}})
(def model-with-congregation-boundary
  (replace-in model [:getting-started :congregation-boundary?] false true))
(def model-with-territories
  (-> model
      (replace-in [:getting-started :territories?] false true)
      (replace-in [:statistics :territories] 0 2)
      (replace-in [:statistics :assigned-territories] 0 1)
      (replace-in [:statistics :covered-in-past-6-months] 0 1)
      (replace-in [:statistics :covered-in-past-12-months] 0 1)
      (replace-in [:statistics :average-assignment-days] nil 60)))
(def demo-model
  {:congregation {:congregation/name "Demo Congregation"}
   :getting-started {:congregation-boundary? false
                     :territories? false}
   :statistics {:territories 0
                :assigned-territories 0
                :covered-in-past-6-months 0
                :covered-in-past-12-months 0
                :average-assignment-days nil}
   :permissions {:view-printouts-page true
                 :view-settings-page false
                 :view-statistics true
                 :view-getting-started false}})

(use-fixtures :once (join-fixtures [(fixed-clock-fixture dmz-test/test-time)
                                    (fn [f]
                                      (binding [publisher/publishers-by-id (fn [_conn _cong-id]
                                                                             nil)]
                                        (f)))]))

(defn- territory-covered-on [date]
  (let [territory-id (random-uuid)]
    [(assoc dmz-test/territory-defined
            :territory/id territory-id)
     (assoc dmz-test/territory-covered
            :territory/id territory-id
            :assignment/covered-date date)]))

(defn- territory-and-assignment [start-date end-date]
  (let [territory-id (random-uuid)]
    [(assoc dmz-test/territory-defined
            :territory/id territory-id)
     (assoc dmz-test/territory-assigned
            :territory/id territory-id
            :assignment/start-date start-date)
     (assoc dmz-test/territory-covered
            :territory/id territory-id
            :assignment/covered-date end-date)
     (assoc dmz-test/territory-returned
            :territory/id territory-id
            :assignment/end-date end-date)]))

(deftest model!-test
  (let [user-id (random-uuid)
        cong-id dmz-test/cong-id
        request {:path-params {:congregation cong-id}}]
    (testutil/with-events (flatten [(assoc dmz-test/congregation-created
                                           :congregation/name "Example Congregation")
                                    (congregation/admin-permissions-granted cong-id user-id)
                                    demo/congregation-created])
      (testutil/with-user-id user-id

        (testing "default"
          (is (= model (congregation-page/model! request))))

        (testing "with a congregation boundary"
          (testutil/with-events [dmz-test/congregation-boundary-defined]
            (is (= model-with-congregation-boundary (congregation-page/model! request)))))

        (testing "with some territories"
          (testutil/with-events [dmz-test/territory-defined
                                 dmz-test/territory-assigned
                                 dmz-test/territory-covered
                                 dmz-test/territory-returned
                                 dmz-test/territory-defined2
                                 (assoc dmz-test/territory-assigned :territory/id dmz-test/territory-id2)]
            (is (= model-with-territories (congregation-page/model! request)))))

        (testing "demo congregation"
          (let [request {:path-params {:congregation "demo"}}]
            (is (= demo-model (congregation-page/model! request)))))

        (testing "counts territories covered within 6 and 12 months"
          (testutil/with-events (flatten [(territory-covered-on dmz-test/today)
                                          (territory-covered-on (-> dmz-test/today (.minusMonths 6) (.plusDays 1)))
                                          ;; -- 6 months --
                                          (territory-covered-on (-> dmz-test/today (.minusMonths 6)))
                                          (territory-covered-on (-> dmz-test/today (.minusMonths 12) (.plusDays 1)))
                                          ;; -- 12 months --
                                          (territory-covered-on (-> dmz-test/today (.minusMonths 12)))])
            (is (= {:territories 5
                    :covered-in-past-6-months 2
                    :covered-in-past-12-months 4}
                   (-> (congregation-page/model! request)
                       :statistics
                       (select-keys [:territories :covered-in-past-6-months :covered-in-past-12-months]))))))

        (testing "calculates the average assignment duration:"
          (let [average-assignment-days #(-> (congregation-page/model! request)
                                             :statistics
                                             :average-assignment-days)]
            (testing "ignores covered but not returned"
              (testutil/with-events [dmz-test/territory-defined
                                     dmz-test/territory-assigned
                                     dmz-test/territory-covered]
                (is (nil? (average-assignment-days)))))

            (testing "ignores returned but not covered"
              (testutil/with-events [dmz-test/territory-defined
                                     dmz-test/territory-assigned
                                     dmz-test/territory-returned]
                (is (nil? (average-assignment-days)))))

            (testing "counts returned and covered"
              (testutil/with-events [dmz-test/territory-defined
                                     dmz-test/territory-assigned
                                     dmz-test/territory-covered
                                     dmz-test/territory-returned]
                (is (= 60 (average-assignment-days)))))

            (testing "calculates the average of all assignments"
              (testutil/with-events (flatten [(territory-and-assignment (LocalDate/of 2000 1 10) (LocalDate/of 2000 1 15)) ; 5 days
                                              (territory-and-assignment (LocalDate/of 2000 1 10) (LocalDate/of 2000 1 20))]) ; 10 days
                (is (= 8 (average-assignment-days))))) ; (5 + 10) / 2 = 7.5 ~= 8

            (testing "minimum duration is 1 day"
              (testutil/with-events (territory-and-assignment (LocalDate/of 2000 1 10) (LocalDate/of 2000 1 10))
                (is (= 1 (average-assignment-days))))
              (testutil/with-events (territory-and-assignment (LocalDate/of 2000 1 10) (LocalDate/of 2000 1 11))
                (is (= 1 (average-assignment-days))))
              (testutil/with-events (territory-and-assignment (LocalDate/of 2000 1 10) (LocalDate/of 2000 1 12))
                (is (= 2 (average-assignment-days)))))

            (testing "includes assignments returned within the last 12 months"
              (testutil/with-events (territory-and-assignment (-> dmz-test/today (.minusMonths 13))
                                                              (-> dmz-test/today (.minusMonths 12)))
                (is (nil? (average-assignment-days))))
              (testutil/with-events (territory-and-assignment (-> dmz-test/today (.minusMonths 13))
                                                              (-> dmz-test/today (.minusMonths 12) (.plusDays 1)))
                (is (= 32 (average-assignment-days)))))))))))

(deftest view-test
  (testing "full permissions"
    (is (= (html/normalize-whitespace
            "Example Congregation

             📍 Territories
             🖨️ Printouts
             ⚙️ Settings

             {info.svg} Territory statistics
             Territories: 0
             Currently assigned: 0 (0 %)
             Covered in past 6 months: 0 (0 %)
             Covered in past 12 months: 0 (0 %)

             {info.svg} Getting started
             ⏳ Define the congregation boundary
             ⏳ Add some territories
             We recommend subscribing to our mailing list to be notified about important Territory Bro updates.")
           (-> (congregation-page/view model)
               html/visible-text))))

  (testing "minimal permissions"
    (is (= (html/normalize-whitespace
            "Example Congregation
             📍 Territories")
           (-> (congregation-page/view (dissoc model :permissions))
               html/visible-text)))))

(deftest statistics-test
  (testing "hidden if the user can't see all territories"
    (is (nil? (congregation-page/statistics (replace-in model [:permissions :view-statistics] true false)))))

  (testing "new congregation"
    (is (= (html/normalize-whitespace
            "{info.svg} Territory statistics
             Territories: 0
             Currently assigned: 0 (0 %)
             Covered in past 6 months: 0 (0 %)
             Covered in past 12 months: 0 (0 %)")
           (-> (congregation-page/statistics model)
               html/visible-text))))

  (testing "with some territories"
    (is (= (html/normalize-whitespace
            "{info.svg} Territory statistics
             Territories: 2
             Currently assigned: 1 (50 %)
             Covered in past 6 months: 1 (50 %)
             Covered in past 12 months: 1 (50 %)
             Average assignment duration: 60 days")
           (-> (congregation-page/statistics model-with-territories)
               html/visible-text)))))

(deftest getting-started-test
  (testing "hidden if the user can't create territories"
    (is (nil? (congregation-page/getting-started (replace-in model [:permissions :view-getting-started] true false)))))

  (testing "new congregation"
    (is (= (html/normalize-whitespace
            "{info.svg} Getting started
             ⏳ Define the congregation boundary
             ⏳ Add some territories
             We recommend subscribing to our mailing list to be notified about important Territory Bro updates.")
           (-> (congregation-page/getting-started model)
               html/visible-text))))

  (testing "with a congregation boundary"
    (is (= (html/normalize-whitespace
            "{info.svg} Getting started
             ✅ Define the congregation boundary
             ⏳ Add some territories
             We recommend subscribing to our mailing list to be notified about important Territory Bro updates.")
           (-> (congregation-page/getting-started model-with-congregation-boundary)
               html/visible-text))))

  (testing "with some territories"
    (is (= (html/normalize-whitespace
            "{info.svg} Getting started
             ⏳ Define the congregation boundary
             ✅ Add some territories
             We recommend subscribing to our mailing list to be notified about important Territory Bro updates.")
           (-> (congregation-page/getting-started model-with-territories)
               html/visible-text)))))
