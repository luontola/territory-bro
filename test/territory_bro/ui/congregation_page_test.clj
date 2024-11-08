(ns territory-bro.ui.congregation-page-test
  (:require [clojure.test :refer :all]
            [matcher-combinators.test :refer :all]
            [territory-bro.domain.congregation :as congregation]
            [territory-bro.domain.dmz-test :as dmz-test]
            [territory-bro.infra.config :as config]
            [territory-bro.test.testutil :as testutil :refer [replace-in]]
            [territory-bro.ui.congregation-page :as congregation-page]
            [territory-bro.ui.html :as html]))

(def model
  {:congregation {:congregation/name "Example Congregation"}
   :statistics {:congregation-boundary? false
                :territories 0}
   :permissions {:view-printouts-page true
                 :view-settings-page true
                 :gis-access true}})
(def model-with-congregation-boundary
  (replace-in model [:statistics :congregation-boundary?] false true))
(def model-with-territories
  (replace-in model [:statistics :territories] 0 1))
(def demo-model
  {:congregation {:congregation/name "Demo Congregation"}
   :statistics {:congregation-boundary? false
                :territories 0}
   :permissions {:view-printouts-page true
                 :view-settings-page false
                 :gis-access false}})

(deftest model!-test
  (let [user-id (random-uuid)
        cong-id dmz-test/cong-id
        request {:path-params {:congregation cong-id}}]
    (binding [config/env {:demo-congregation cong-id}]
      (testutil/with-events (flatten [(assoc dmz-test/congregation-created
                                             :congregation/name "Example Congregation")
                                      (congregation/admin-permissions-granted cong-id user-id)])
        (testutil/with-user-id user-id

          (testing "default"
            (is (= model (congregation-page/model! request))))

          (testing "with a congregation boundary"
            (testutil/with-events [dmz-test/congregation-boundary-defined]
              (is (= model-with-congregation-boundary (congregation-page/model! request)))))

          (testing "with some territories"
            (testutil/with-events [dmz-test/territory-defined]
              (is (= model-with-territories (congregation-page/model! request)))))

          (testing "demo congregation"
            (let [request {:path-params {:congregation "demo"}}]
              (is (= demo-model (congregation-page/model! request))))))))))

(deftest view-test
  (testing "full permissions"
    (is (= (html/normalize-whitespace
            "Example Congregation

             {info.svg} Getting started
             ⏳ Define the congregation boundary
             ⏳ Add some territories
             We recommend subscribing to our mailing list to be notified about important Territory Bro updates.

             Territories
             Printouts
             Settings")
           (-> (congregation-page/view model)
               html/visible-text))))

  (testing "minimal permissions"
    (is (= (html/normalize-whitespace
            "Example Congregation
             Territories")
           (-> (congregation-page/view (dissoc model :permissions))
               html/visible-text)))))

(deftest getting-started-test
  (testing "hidden if the user can't create territories themselves"
    (is (nil? (congregation-page/getting-started (replace-in model [:permissions :gis-access] true false)))))

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
