;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.infra.middleware-test
  (:require [clojure.test :refer :all]
            [ring.util.http-response :as http-response]
            [ring.util.response :as response]
            [territory-bro.infra.middleware :as middleware]))

(deftest wrap-cache-control-test
  (testing "SSR pages are not cached"
    (let [handler (-> (constantly (http-response/ok ""))
                      middleware/wrap-cache-control)]
      (is (= {:status 200
              :headers {"Cache-Control" "private, no-cache"}
              :body ""}
             (handler {:request-method :get
                       :uri "/"})
             (handler {:request-method :get
                       :uri "/some/page"})))))

  (testing "if response contains a custom cache-control header, that one is used"
    (let [handler (-> (constantly (-> (http-response/ok "")
                                      (response/header "Cache-Control" "custom value")))
                      middleware/wrap-cache-control)]
      (is (= {:status 200
              :headers {"Cache-Control" "custom value"}
              :body ""}
             (handler {:request-method :get
                       :uri "/"})
             (handler {:request-method :get
                       :uri "/some/page"})))))

  (testing "static assets are cached for one hour"
    (let [handler (-> (constantly (http-response/ok ""))
                      middleware/wrap-cache-control)]
      (is (= {:status 200
              :headers {"Cache-Control" "public, max-age=3600, stale-while-revalidate=86400"}
              :body ""}
             (handler {:request-method :get
                       :uri "/favicon.ico"})
             (handler {:request-method :get
                       :uri "/assets/style.css"})))))

  (testing "static assets with a content hash are cached indefinitely"
    (let [handler (-> (constantly (http-response/ok ""))
                      middleware/wrap-cache-control)]
      (is (= {:status 200
              :headers {"Cache-Control" "public, max-age=2592000, immutable"}
              :body ""}
             (handler {:request-method :get
                       :uri "/assets/style-4da573e6.css"})
             (handler {:request-method :get
                       :uri "/assets/image-28ead48996a4ca92f07ee100313e57355dbbcbf2.svg"})))))

  (testing "error responses are not cached"
    (let [handler (-> (constantly (http-response/not-found ""))
                      middleware/wrap-cache-control)]
      (is (= {:status 404
              :headers {"Cache-Control" "private, no-cache"}
              :body ""}
             (handler {:request-method :get
                       :uri "/some/page"})
             (handler {:request-method :get
                       :uri "/assets/style.css"})
             (handler {:request-method :get
                       :uri "/assets/style-4da573e6.css"}))))))
