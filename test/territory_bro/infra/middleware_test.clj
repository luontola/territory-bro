(ns territory-bro.infra.middleware-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer :all]
            [ring.mock.request :as mock]
            [ring.util.http-response :as http-response]
            [ring.util.response :as response]
            [territory-bro.infra.middleware :as middleware])
  (:import (java.time Duration)))

(deftest wrap-compressed-resources-test
  (let [handler (-> (constantly ::handler-response)
                    (middleware/wrap-compressed-resources "/public"))
        simplify (fn [response]
                   (update response :headers dissoc "Last-Modified"))]

    (testing "delegates to handler if resource is not found"
      (is (= ::handler-response
             (handler (mock/request :get "/some/page")))))

    (testing "returns smallest compressed resource"
      (is (= {:status 200
              :body (.getAbsoluteFile (io/file "test-resources/public/testfile.txt.br"))
              :headers {"Content-Encoding" "br"
                        "Content-Length" "101"}}
             (simplify (handler (-> (mock/request :get "/testfile.txt")
                                    (mock/header "accept-encoding" "gzip, deflate, br, zstd")))))))

    (testing "returns only encodings listed in the accept-encoding header"
      (is (= {:status 200
              :body (.getAbsoluteFile (io/file "test-resources/public/testfile.txt.gz"))
              :headers {"Content-Encoding" "gzip"
                        "Content-Length" "111"}}
             (simplify (handler (-> (mock/request :get "/testfile.txt")
                                    ;; weighted values should work too, though their weights are ignored
                                    (mock/header "accept-encoding" "deflate, gzip;q=1.0, *;q=0.5")))))))

    (testing "returns the uncompressed resource if there is no accept-encoding header"
      (is (= {:status 200
              :body (.getAbsoluteFile (io/file "test-resources/public/testfile.txt"))
              :headers {"Content-Length" "124"}}
             (simplify (handler (mock/request :get "/testfile.txt"))))))

    (testing "returns the uncompressed resource if it's not pre-compressed"
      (is (= {:status 200
              :body (.getAbsoluteFile (io/file "resources/public/assets/examples/card.fef9840a.jpg"))
              :headers {"Content-Length" "217134"}}
             (simplify (handler (-> (mock/request :get "/assets/examples/card.fef9840a.jpg")
                                    (mock/header "accept-encoding" "gzip, deflate, br, zstd")))))))))

(deftest wrap-cache-control-test
  (testing "SSR pages are not cached"
    (let [handler (-> (constantly (http-response/ok ""))
                      middleware/wrap-cache-control)]
      (is (= {:status 200
              :headers {"Cache-Control" "private, no-cache"}
              :body ""}
             (handler (mock/request :get "/"))
             (handler (mock/request :get "/some/page"))))))

  (testing "if response contains a custom cache-control header, that one is used"
    (let [handler (-> (constantly (-> (http-response/ok "")
                                      (response/header "Cache-Control" "custom value")))
                      middleware/wrap-cache-control)]
      (is (= {:status 200
              :headers {"Cache-Control" "custom value"}
              :body ""}
             (handler (mock/request :get "/"))
             (handler (mock/request :get "/some/page"))))))

  (let [cache-for-1-hour (str "public, max-age=" (.toSeconds (Duration/ofHours 1))
                              ", stale-while-revalidate=" (.toSeconds (Duration/ofHours 24)))
        cache-indefinitely (str "public, max-age=" (.toSeconds (Duration/ofDays 365)) ", immutable")]
    (testing "static assets are cached for one hour"
      (let [handler (-> (constantly (http-response/ok ""))
                        middleware/wrap-cache-control)]
        (is (= {:status 200
                :headers {"Cache-Control" cache-for-1-hour}
                :body ""}
               (handler (mock/request :get "/favicon.ico"))
               (handler (mock/request :get "/assets/style.css"))))))

    (testing "static assets with a content hash are cached indefinitely"
      (let [handler (-> (constantly (http-response/ok ""))
                        middleware/wrap-cache-control)]
        (is (= {:status 200
                :headers {"Cache-Control" cache-indefinitely}
                :body ""}
               (handler (mock/request :get "/assets/style.4da573e6.css"))
               (handler (mock/request :get "/assets/image.28ead48996a4ca92f07ee100313e57355dbbcbf2.svg"))))))

    (testing "content-hashed filename format tries to avoid conflicts with real words"
      (let [handler (-> (constantly (http-response/ok ""))
                        middleware/wrap-cache-control)]
        (is (= {:status 200
                :headers {"Cache-Control" cache-for-1-hour}
                :body ""}
               ;; Using "." as the content hash separator is better than using "-",
               ;; which is also used as a word separator and can be ambiguous.
               (handler (mock/request :get "/assets/style-cafebabe.css")))))))

  (testing "error responses are not cached"
    (let [handler (-> (constantly (http-response/not-found ""))
                      middleware/wrap-cache-control)]
      (is (= {:status 404
              :headers {"Cache-Control" "private, no-cache"}
              :body ""}
             (handler (mock/request :get "/some/page"))
             (handler (mock/request :get "/assets/style.css"))
             (handler (mock/request :get "/assets/style.4da573e6.css")))))))

(deftest error-reporting-test
  (let [simplify (fn [response]
                   (update response :headers dissoc
                           "Cache-Control"
                           "X-Content-Type-Options"
                           "X-Frame-Options"
                           "Set-Cookie"))]
    (testing "handler throws arbitrary exception"
      (let [handler (-> (fn [_]
                          (throw (RuntimeException. "dummy")))
                        middleware/wrap-base)]
        (is (= {:status 500
                :body "Internal Server Error"
                :headers {"Content-Type" "text/html; charset=utf-8"}}
               (handler (mock/request :get "/some/page"))))))

    (testing "handler throws http-response with string body"
      (let [handler (-> (fn [_]
                          (http-response/conflict! "dummy"))
                        middleware/wrap-base)]
        (is (= {:status 409
                :body "dummy"
                :headers {"Content-Type" "text/plain; charset=utf-8"
                          "Content-Length" "5"}}
               (-> (handler (mock/request :get "/some/page"))
                   (simplify))))))

    (testing "handler throws http-response with map body"
      (let [handler (-> (fn [_]
                          (http-response/conflict! {:message "dummy"}))
                        middleware/wrap-base)]
        (is (= {:status 409
                :body "{\"message\":\"dummy\"}"
                :headers {"Content-Type" "application/json; charset=utf-8"
                          "Content-Length" "19"}}
               (-> (handler (mock/request :get "/some/page"))
                   (update :body slurp)
                   (simplify))))))))
