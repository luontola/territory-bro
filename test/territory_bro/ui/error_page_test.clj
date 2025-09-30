(ns territory-bro.ui.error-page-test
  (:require [clojure.test :refer :all]
            [ring.util.http-response :as http-response]
            [territory-bro.ui.error-page :as error-page]
            [territory-bro.ui.html :as html]))

(def layout-header
  "Territory Bro

   🏠 Home
   📖 Documentation
   ✍️ Registration
   📢 News {external-link.svg}
   🛟 Support

   {language.svg} Change language [English]
   Login

   Sorry, something went wrong 🥺
   Close ")

(deftest wrap-error-pages-test
  (let [handle-request-response (fn [request response]
                                  (let [handler (-> (constantly response)
                                                    error-page/wrap-error-pages)]
                                    (-> (handler request)
                                        (update :body html/visible-text))))
        handle-response (fn [response]
                          (handle-request-response {:uri "/"} response))]

    (testing "500 Internal Server Error"
      (is (= {:status 500
              :body (html/normalize-whitespace
                     (str "Sorry, something went wrong 🥺 - "
                          layout-header
                          "Sorry, something went wrong 🥺
                           Return to the front page and try again"))
              :headers {"Content-Type" "text/html"}}
             (handle-response (http-response/internal-server-error)))))

    (testing "404 Not Found"
      (is (= {:status 404
              :body (html/normalize-whitespace
                     (str "Page not found 😵 - "
                          layout-header
                          "Page not found 😵
                           Return to the front page and try again"))
              :headers {"Content-Type" "text/html"}}
             (handle-response (http-response/not-found)))))

    (testing "403 Forbidden"
      (is (= {:status 403
              :body (html/normalize-whitespace
                     (str "Access denied 🛑 - "
                          layout-header
                          "Access denied 🛑
                           Return to the front page and try again"))
              :headers {"Content-Type" "text/html"}}
             (handle-response (http-response/forbidden)))))

    (testing "ignores custom error pages (i.e. html responses)"
      (is (= {:status 403
              :body "Custom error with descriptive explanation."
              :headers {"Content-Type" "text/html"}}
             (handle-response {:status 403
                               :body "<h1>Custom error</h1><p>with descriptive explanation.</p>"
                               :headers {"Content-Type" "text/html"}}))))

    (testing "ignores htmx requests; the response body will be shown in the error message, so it should not be HTML"
      (is (= {:status 403
              :body "original"
              :headers {}}
             (handle-request-response {:uri "/"
                                       :headers {"hx-request" "true"}}
                                      (http-response/forbidden "original")))))

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
