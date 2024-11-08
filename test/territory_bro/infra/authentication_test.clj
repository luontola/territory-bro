(ns territory-bro.infra.authentication-test
  (:require [clojure.test :refer :all]
            [territory-bro.infra.authentication :as auth]))

(deftest logged-in-or-anonymous-test
  (testing "nil user"
    (is (false? (auth/logged-in? nil)))
    (is (true? (auth/anonymous? nil)))
    (binding [auth/*user* nil]
      (is (false? (auth/logged-in?)))
      (is (true? (auth/anonymous?)))))

  (testing "anonymous user"
    (let [user-id auth/anonymous-user-id]
      (is (some? user-id))
      (is (false? (auth/logged-in? user-id)))
      (is (true? (auth/anonymous? user-id)))
      (binding [auth/*user* {:user/id user-id}]
        (is (false? (auth/logged-in?)))
        (is (true? (auth/anonymous?))))))

  (testing "logged in user"
    (let [user-id (random-uuid)]
      (is (true? (auth/logged-in? user-id)))
      (is (false? (auth/anonymous? user-id)))
      (binding [auth/*user* {:user/id user-id}]
        (is (true? (auth/logged-in?)))
        (is (false? (auth/anonymous?)))))))
