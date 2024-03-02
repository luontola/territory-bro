;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.error-page-test
  (:require [clojure.test :refer :all]
            [ring.util.http-response :as http-response]
            [territory-bro.ui.error-page :as error-page]
            [territory-bro.ui.html :as html]))

(def layout-header
  "Territory Bro

   🏠 Home
   User guide {fa-external-link-alt}
   News {fa-external-link-alt}
   🛟 Support
   Login

   Sorry, something went wrong 🥺
   Close ")

(deftest wrap-error-pages-test
  (let [handle-response (fn [response]
                          (let [request {:uri "/"}
                                handler (-> (constantly response)
                                            error-page/wrap-error-pages)]
                            (-> (handler request)
                                (update :body html/visible-text))))]

    (testing "500 Internal Server Error"
      (is (= {:status 500
              :body (html/normalize-whitespace
                     (str layout-header
                          "Sorry, something went wrong 🥺
                           Return to the front page and try again"))
              :headers {"Content-Type" "text/html"}}
             (handle-response (http-response/internal-server-error)))))

    (testing "404 Not Found"
      (is (= {:status 404
              :body (html/normalize-whitespace
                     (str layout-header
                          "Page not found 😵
                           Return to the front page and try again"))
              :headers {"Content-Type" "text/html"}}
             (handle-response (http-response/not-found)))))

    (testing "403 Forbidden"
      (is (= {:status 403
              :body (html/normalize-whitespace
                     (str layout-header
                          "Access denied 🛑
                           Return to the front page and try again"))
              :headers {"Content-Type" "text/html"}}
             (handle-response (http-response/forbidden)))))

    (testing "ignores 401 Unauthorized; it's handled by territory-bro.infra.auth0/wrap-redirect-to-login"
      (is (= {:status 401
              :body "original"
              :headers {}}
             (handle-response (http-response/unauthorized "original")))))

    (testing "ignores 400 Bad Request; pages may use it to show validation errors"
      (is (= {:status 400
              :body "original"
              :headers {}}
             (handle-response (http-response/bad-request "original")))))

    (testing "ignores 3xx"
      (is (= {:status 303
              :body ""
              :headers {"Location" "/original"}}
             (handle-response (http-response/see-other "/original")))))

    (testing "ignores 2xx"
      (is (= {:status 200
              :body "original"
              :headers {}}
             (handle-response (http-response/ok "original")))))))
