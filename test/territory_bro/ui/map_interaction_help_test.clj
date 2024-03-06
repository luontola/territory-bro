;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.map-interaction-help-test
  (:require [clojure.test :refer :all]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.map-interaction-help :as map-interaction-help]))

(def default-model {:mac? false})
(def mac-model {:mac? true})

(deftest model-test
  (testing "Windows Firefox"
    (is (= default-model
           (map-interaction-help/model {:headers {"user-agent" "Mozilla/5.0 (Windows NT 6.1; Win64; x64; rv:47.0) Gecko/20100101 Firefox/47.0"}}))))

  (testing "macOS Chrome"
    (is (= mac-model
           (map-interaction-help/model {:headers {"user-agent" "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"}}))))

  (testing "iPhone Safari"
    (is (= mac-model
           (map-interaction-help/model {:headers {"user-agent" "Mozilla/5.0 (iPhone; CPU iPhone OS 13_5_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/13.1.1 Mobile/15E148 Safari/604.1"}})))))

(deftest view-test
  (testing "Windows and Linux"
    (is (= (html/normalize-whitespace
            "{fa-info-circle} How to interact with the maps?
             Move: drag with two fingers / drag with the left mouse button
             Zoom: pinch or spread with two fingers / hold Ctrl and scroll with the mouse wheel
             Rotate: rotate with two fingers / hold Alt + Shift and drag with the left mouse button")
           (-> (map-interaction-help/view default-model)
               html/visible-text))))

  (testing "macOS"
    (is (= (html/normalize-whitespace
            "{fa-info-circle} How to interact with the maps?
             Move: drag with two fingers / drag with the left mouse button
             Zoom: pinch or spread with two fingers / hold ⌘ Command and scroll with the mouse wheel
             Rotate: rotate with two fingers / hold ⌥ Option + ⇧ Shift and drag with the left mouse button")
           (-> (map-interaction-help/view mac-model)
               html/visible-text)))))
