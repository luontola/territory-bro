;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.i18n-test
  (:require [clojure.test :refer :all]
            [ring.middleware.cookies :as cookies]
            [territory-bro.ui.i18n :as i18n]))

(deftest t-test
  (testing "returns translation in the specified language"
    (binding [i18n/*lang* :fi]
      (is (= "Etusivu" (i18n/t "HomePage.title")))))

  (testing "language defaults to English"
    (is (= "Home" (i18n/t "HomePage.title"))))

  (testing "fallback: unsupported language"
    (binding [i18n/*lang* :xx]
      (is (= "HomePage.title" (i18n/t "HomePage.title")))))

  (testing "fallback: non-existing translation key"
    (is (= "foo.bar" (i18n/t "foo.bar")))))

(deftest languages-test
  (testing "lists all supported languages, sorted by native name"
    (is (= [{:code "en", :englishName "English", :nativeName "English"}
            {:code "es", :englishName "Spanish", :nativeName "español"}
            {:code "id", :englishName "Indonesian", :nativeName "Indonesia"}
            {:code "it", :englishName "Italian", :nativeName "Italiano"}
            {:code "nl", :englishName "Dutch", :nativeName "Nederlands"}
            {:code "pt", :englishName "Portuguese", :nativeName "Português"}
            {:code "fi", :englishName "Finnish", :nativeName "suomi"}]
           (i18n/languages)))))

(deftest wrap-current-language-test
  (let [handler (-> (fn [_request]
                      {:body {:lang i18n/*lang*}})
                    i18n/wrap-current-language
                    cookies/wrap-cookies)]
    (testing "default language from system defaults"
      (is (= {:body {:lang :en}}
             (handler {}))))

    (testing "default language from Accept-Language request header"
      (is (= {:body {:lang :fi}}
             (handler {:headers {"accept-language" "fi,en-GB;q=0.9,en-US;q=0.8,en;q=0.7",}}))
          "match found")
      (is (= {:body {:lang :en}}
             (handler {:headers {"accept-language" "xx"}}))
          "no match")
      (is (= {:body {:lang :en}}
             (handler {:headers {"accept-language" "*"}}))
          "wildcard"))

    (testing "language from cookie"
      (is (= {:body {:lang :fi}}
             (handler {:cookies {"lang" {:value "fi"}}}))))

    (testing "change language, save as cookie"
      (is (= {:body {:lang :fi}
              :headers {"Set-Cookie" ["lang=fi; Max-Age=31536000; Path=/"]}}
             (handler {:params {:lang "fi"}}))))

    (testing "ignores unknown language codes"
      (is (= {:body {:lang :en}}
             (handler {:cookies {"lang" {:value "xx"}}}))))))
