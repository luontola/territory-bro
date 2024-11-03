(ns territory-bro.ui.hiccup-test
  (:require [clojure.test :refer :all]
            [territory-bro.ui.hiccup :as h])
  (:import (hiccup.util RawString)))

(deftest html-test
  (is (instance? RawString (h/html)))
  (is (= "" (str (h/html))))

  (testing "produces HTML 5"
    (testing "at compile time"
      (is (= "<p flag>foo</p>" (str (h/html [:p {:flag true} "foo"]))))
      (is (= "<br>" (str (h/html [:br])))))

    (testing "at runtime"
      (is (= "<p flag>foo</p>" (str (h/html (identity [:p {:flag true} "foo"])))))
      (is (= "<br>" (str (h/html (identity [:br])))))))

  (testing "escapes text"
    (testing "at compile time"
      (is (= "<p>&lt;script&gt;</p>" (str (h/html [:p "<script>"])))))

    (testing "at runtime"
      (is (= "<p>&lt;script&gt;</p>" (str (h/html [:p (identity "<script>")])))))))
