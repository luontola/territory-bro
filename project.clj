; Copyright © 2015-2018 Esko Luontola
; This software is released under the Apache License 2.0.
; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(defproject territory-bro "1.0.0-SNAPSHOT"

  :description "Territory Bro is a tool for managing territory cards in the congregations of Jehovah's Witnesses."
  :url "https://territorybro.com"

  :dependencies [[com.auth0/java-jwt "3.4.1"]
                 [com.auth0/jwks-rsa "0.6.1"]
                 [compojure "1.6.1"]
                 [conman "0.8.2"]
                 [cprop "0.1.11"]
                 [liberator "0.15.2"]
                 [luminus-immutant "0.2.4"]
                 [luminus-migrations "0.5.0"]
                 [luminus-nrepl "0.1.4"]
                 [metosin/ring-http-response "0.9.0"]
                 [metosin/ring-middleware-format "0.6.0"]
                 [mount "0.1.12"]
                 [org.clojars.luontola/ns-tracker "0.3.1-patch1"]
                 [org.clojure/clojure "1.9.0"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/tools.cli "0.3.7"]
                 [org.clojure/tools.logging "0.4.1"]
                 [org.postgresql/postgresql "42.2.5"]
                 [ring/ring-core "1.7.0"]
                 [ring/ring-defaults "0.3.2"]
                 [selmer "1.11.7"]]

  :min-lein-version "2.0.0"

  :source-paths ["src"]
  :test-paths ["test"]
  :resource-paths ["resources"]
  :target-path "target/%s/"
  :main ^:skip-aot territory-bro.main

  :plugins [[com.jakemccrary/lein-test-refresh "0.14.0"]
            [lein-ancient "0.6.15"]]

  :profiles {:uberjar {:omit-source true
                       :aot :all
                       :uberjar-name "territory-bro.jar"
                       :source-paths ["env/prod/clj"]
                       :resource-paths ["env/prod/resources"]}

             :dev [:project/dev :profiles/dev]
             :test [:project/test :profiles/test]

             :project/dev {:dependencies [[bananaoomarang/ring-debug-logging "1.1.0"]
                                          [pjstadig/humane-test-output "0.8.3"]
                                          [prone "1.6.0"]
                                          [ring/ring-devel "1.7.0" :exclusions [ns-tracker]]
                                          [ring/ring-mock "0.3.2"]]

                           :source-paths ["env/dev/clj"]
                           :resource-paths ["env/dev/resources"]
                           :repl-options {:init-ns user}
                           :injections [(require 'pjstadig.humane-test-output)
                                        (pjstadig.humane-test-output/activate!)]}
             :project/test {:resource-paths ["env/test/resources"]}
             :profiles/dev {}
             :profiles/test {}})
