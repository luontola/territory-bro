;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns ^:e2e ^:prod territory-bro.prod-test
  (:require [clj-http.client :as http]
            [clojure.string :as str]
            [clojure.test :refer :all]
            [territory-bro.test.fixtures :refer :all]))

(deftest http-redirects-test
  (testing "shared link"
    (let [response (http/get "https://territorybro.com/share/demo-5qs_xGRJTciwVAVkvuUX8A/1")]
      (is (= 200 (:status response)))
      (is (str/includes? (:body response) "Territory 1 - Territory Bro"))
      (is (= ["https://territorybro.com/congregation/demo/territories/e6ab3fc4-6449-4dc8-b054-0564bee517f0?share-key=demo-5qs_xGRJTciwVAVkvuUX8A"]
             (:trace-redirects response)))))

  (testing "shared link, old beta.territorybro.com domain"
    (let [response (http/get "https://beta.territorybro.com/share/demo-5qs_xGRJTciwVAVkvuUX8A/1")]
      (is (= 200 (:status response)))
      (is (str/includes? (:body response) "Territory 1 - Territory Bro"))
      (is (= ["https://territorybro.com/share/demo-5qs_xGRJTciwVAVkvuUX8A/1"
              "https://territorybro.com/congregation/demo/territories/e6ab3fc4-6449-4dc8-b054-0564bee517f0?share-key=demo-5qs_xGRJTciwVAVkvuUX8A"]
             (:trace-redirects response)))))

  (testing "QR code"
    (let [response (http/get "https://qr.territorybro.com/demo-5qs_xGRJTciwVAVkvuUX8A")]
      (is (= 200 (:status response)))
      (is (str/includes? (:body response) "Territory 1 - Territory Bro"))
      (is (= ["https://territorybro.com/share/demo-5qs_xGRJTciwVAVkvuUX8A"
              "https://territorybro.com/congregation/demo/territories/e6ab3fc4-6449-4dc8-b054-0564bee517f0?share-key=demo-5qs_xGRJTciwVAVkvuUX8A"]
             (:trace-redirects response))))))
