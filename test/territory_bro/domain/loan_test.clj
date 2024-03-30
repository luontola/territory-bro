;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.domain.loan-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [territory-bro.domain.loan :as loan]
            [territory-bro.test.testutil :refer [re-equals thrown-with-msg?]])
  (:import (java.util UUID)))

(deftest ^:slow download-test
  (testing "no url"
    (is (nil? (loan/download! nil))))

  (testing "downloads files from Google Docs"
    (is (str/includes?
         (loan/download! "https://docs.google.com/")
         "<html")))

  (testing "will not read local files"
    (is (thrown-with-msg?
         IllegalArgumentException (re-equals "Disallowed protocol: file:/etc/passwd")
         (loan/download! "file:/etc/passwd"))))

  (testing "will not download from other hosts"
    (is (thrown-with-msg?
         IllegalArgumentException (re-equals "Disallowed host: https://example.com/")
         (loan/download! "https://example.com/")))))

(deftest parse-loans-csv-test
  (testing "no file"
    (is (empty? (loan/parse-loans-csv nil))))

  (testing "header only"
    (is (empty? (loan/parse-loans-csv "Number,Loaned,Staleness"))))

  (testing "full data"
    (is (= [{:territory/number "101"
             :territory/loaned? false
             :territory/staleness 4}
            {:territory/number "102"
             :territory/loaned? true
             :territory/staleness 1}
            {:territory/number "103"
             :territory/loaned? false
             :territory/staleness 0}]
           (loan/parse-loans-csv
            (str "Number,Loaned,Staleness\n"
                 "101,FALSE,4\n"
                 "102,TRUE,1\n"
                 "103,FALSE,0")))))

  (testing "supports quoted values"
    (is (= [{:territory/number "101, A"
             :territory/loaned? true
             :territory/staleness 4}]
           (loan/parse-loans-csv
            (str "Number,Loaned,Staleness\n"
                 "\"101, A\",TRUE,4\n")))))

  (testing "ignores empty rows"
    (is (= [{:territory/number "101"
             :territory/loaned? false
             :territory/staleness 4}
            {:territory/number "103"
             :territory/loaned? false
             :territory/staleness 0}]
           (loan/parse-loans-csv
            (str "Number,Loaned,Staleness\n"
                 "101,FALSE,4\n"
                 ",,\n"
                 "103,FALSE,0\n")))))

  (testing "ignores other columns"
    (is (= [{:territory/number "101"
             :territory/loaned? false
             :territory/staleness 4}]
           (loan/parse-loans-csv
            (str "Number,foo,Loaned,bar,,,Staleness\n"
                 "101,,FALSE,,,,4"))))))

(deftest enrich-territory-loans!-test
  (let [congregation {:congregation/id (UUID. 0 1)
                      :congregation/territories [{:territory/number "101"}
                                                 {:territory/number "102"}
                                                 {:territory/number "103"}]}
        loans-csv (str "Number,Loaned,Staleness\n"
                       "101,TRUE,1\n"
                       ",,\n" ; CSV has no data for 102 -> don't add loans data for 102
                       "103,FALSE,3\n"
                       "104,FALSE,4\n")] ; CSV has data for 104, which doesn't exist -> ignore silently
    (is (= {:congregation/id (UUID. 0 1)
            :congregation/territories [{:territory/number "101"
                                        :territory/loaned? true
                                        :territory/staleness 1}
                                       {:territory/number "102"}
                                       {:territory/number "103"
                                        :territory/loaned? false
                                        :territory/staleness 3}]}
           (binding [loan/download! (constantly loans-csv)]
             (loan/enrich-territory-loans! congregation))))))
