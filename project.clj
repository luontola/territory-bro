;; Copyright © 2015-2020 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(defproject territory-bro "1.0.0-SNAPSHOT"

  :description "Territory Bro is a tool for managing territory cards in the congregations of Jehovah's Witnesses."
  :url "https://territorybro.com"

  :dependencies [[camel-snake-kebab "0.4.0"]
                 [com.attendify/schema-refined "0.3.0-alpha4"]
                 [com.auth0/java-jwt "3.8.2"]
                 [com.auth0/jwks-rsa "0.8.3"]
                 [com.fasterxml.jackson.core/jackson-core "2.9.9"]
                 [com.fasterxml.jackson.core/jackson-databind "2.9.9.1"]
                 [com.fasterxml.jackson.datatype/jackson-datatype-jsr310 "2.9.9"]
                 [com.layerware/hugsql "0.4.9"]
                 [compojure "1.6.1"]
                 [conman "0.8.3"]
                 [cprop "0.1.14"]
                 [liberator "0.15.3"]
                 [luminus-immutant "0.2.5"]
                 [luminus-nrepl "0.1.6"]
                 [medley "1.2.0"]
                 [metosin/jsonista "0.2.4"]
                 [metosin/ring-http-response "0.9.1"]
                 [metosin/ring-middleware-format "0.6.0"] ;; TODO: replace with newer library
                 [metosin/schema-tools "0.12.0"]
                 [mount "0.1.16"]
                 [org.clojars.luontola/ns-tracker "0.3.1-patch1"]
                 [org.clojure/clojure "1.10.1"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/test.check "0.10.0"]
                 [org.clojure/tools.cli "0.4.2"]
                 [org.clojure/tools.logging "0.5.0"]
                 [org.clojure/tools.reader "1.3.2"] ;; XXX: overrides old version from metosin/ring-middleware-format
                 [org.flywaydb/flyway-core "6.0.1"]
                 [org.postgresql/postgresql "42.2.6"]
                 [prismatic/schema "1.1.12"]
                 [prismatic/schema-generators "0.1.3"]
                 [ring-logger "1.0.1"]
                 [ring/ring-core "1.7.1"]
                 [ring/ring-defaults "0.3.2"]]

  :min-lein-version "2.0.0"

  :source-paths ["src"]
  :java-source-paths ["src-java"]
  :javac-options ["-source" "8" "-target" "8"]
  :test-paths ["test"]
  :resource-paths ["resources"]
  :target-path "target/%s/"
  :main ^:skip-aot territory-bro.main
  :global-vars {*warn-on-reflection* true
                *print-namespace-maps* false}

  :plugins [[lein-ancient "0.6.15"]]

  :aliases {"autotest" ["kaocha" "--watch"]
            "kaocha" ["with-profile" "+kaocha,+test" "run" "-m" "kaocha.runner"]}

  :profiles {:uberjar {:omit-source true
                       :aot :all
                       :uberjar-name "territory-bro.jar"}

             :kaocha {:dependencies [[lambdaisland/kaocha "0.0-573"]]}

             :dev [:project/dev :profiles/dev]
             :test [:project/test :profiles/test]

             :project/dev {:dependencies [[bananaoomarang/ring-debug-logging "1.1.0"]
                                          [pjstadig/humane-test-output "0.9.0"]
                                          [ring/ring-devel "1.7.1" :exclusions [ns-tracker]]
                                          [ring/ring-mock "0.4.0"]]

                           :jvm-opts ["-Dconf=dev-config.edn"]
                           :repl-options {:init-ns user}
                           :injections [(require 'pjstadig.humane-test-output)
                                        (pjstadig.humane-test-output/activate!)]}
             :project/test {:jvm-opts ["-Dconf=test-config.edn"]}
             :profiles/dev {}
             :profiles/test {}})
