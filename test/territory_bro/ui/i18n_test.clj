(ns territory-bro.ui.i18n-test
  (:require [clojure.test :refer :all]
            [ring.middleware.cookies :as cookies]
            [territory-bro.ui.i18n :as i18n]))

(deftest flatten-map-test
  (let [flatten-map @#'i18n/flatten-map]
    (is (= {} (flatten-map {} nil)))
    (is (= {"foo" "bar"} (flatten-map {:foo "bar"} nil)))
    (is (= {"foo.bar" "gazonk"} (flatten-map {:foo {:bar "gazonk"}} nil)))
    (is (= {"foo" 1
            "bar" 2}
           (flatten-map {:foo 1
                         :bar 2}
                        nil)))
    (is (= {"root.foo" 1
            "root.bar" 2}
           (flatten-map {:root {:foo 1
                                :bar 2}}
                        nil)))))

(deftest t-test
  (testing "returns translation in the specified language"
    (binding [i18n/*lang* :fi]
      (is (= "Etusivu" (i18n/t "HomePage.title")))))

  (testing "language defaults to English"
    (is (= "Home" (i18n/t "HomePage.title"))))

  (testing "fallback: unsupported language -> English"
    (binding [i18n/*lang* :xx]
      (is (= "Home" (i18n/t "HomePage.title")))))

  (testing "fallback: non-existing translation key -> show key"
    (is (= "foo.bar" (i18n/t "foo.bar"))))

  (testing "data format matches localization keys"
    (is (= "Home" (get-in (i18n/i18n) [:resources :en "HomePage.title"])))))

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
                    cookies/wrap-cookies)
        en-cookie {"Set-Cookie" ["lang=en; Max-Age=31536000; Path=/"]}
        fi-cookie {"Set-Cookie" ["lang=fi; Max-Age=31536000; Path=/"]}]
    (testing "default language from system defaults"
      (is (= {:body {:lang :en}
              :headers en-cookie}
             (handler {}))))

    (testing "default language from Accept-Language request header"
      (is (= {:body {:lang :fi}
              :headers fi-cookie}
             (handler {:headers {"accept-language" "fi,en-GB;q=0.9,en-US;q=0.8,en;q=0.7",}}))
          "match found")
      (is (= {:body {:lang :en}
              :headers en-cookie}
             (handler {:headers {"accept-language" "xx"}}))
          "no match")
      (is (= {:body {:lang :en}
              :headers en-cookie}
             (handler {:headers {"accept-language" "*"}}))
          "wildcard"))

    (testing "language from cookie (does not set a new cookie)"
      (is (= {:body {:lang :fi}}
             (handler {:cookies {"lang" {:value "fi"}}}))))

    (testing "change language using query parameter"
      (is (= {:body {:lang :fi}
              :headers fi-cookie}
             (handler {:params {:lang "fi"}}))))

    (testing "ignores unknown language codes"
      (is (= {:body {:lang :en}
              :headers en-cookie}
             (handler {:cookies {"lang" {:value "xx"}}}))))))
