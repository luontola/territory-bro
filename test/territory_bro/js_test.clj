;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.js-test
  (:require [clojure.test :refer :all])
  (:import (org.graalvm.polyglot Context)))

(deftest hello-world
  (with-open [context (Context/create (make-array String 0))]
    (let [js-fn (.eval context "js" "(function myFun(param){ return 'hello '+param; })")
          result (.execute js-fn (into-array ["world"]))]
      (is (true? (.isString result)))
      (is (= "hello world" (.asString result))))))
