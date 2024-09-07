;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(defproject territory-bro "1.0.0-SNAPSHOT"

  :description "Territory Bro is a tool for managing territory cards in the congregations of Jehovah's Witnesses."
  :url "https://territorybro.com"

  :dependencies [[ch.qos.logback/logback-classic "1.5.8"]
                 [ch.qos.logback/logback-core "1.5.8"]
                 [com.attendify/schema-refined "0.3.0-alpha4"]
                 [com.auth0/java-jwt "4.4.0"]
                 [com.auth0/jwks-rsa "0.22.1"]
                 [com.auth0/mvc-auth-commons "1.11.0"]
                 [com.layerware/hugsql "0.5.3"]
                 [cprop "0.1.20"]
                 [enlive "1.1.6"]
                 [hiccup "2.0.0-RC3"]
                 [hikari-cp "3.1.0"]
                 [io.nayuki/qrcodegen "1.8.0"]
                 [medley "1.4.0"]
                 [metosin/jsonista "0.3.10"]
                 [metosin/reitit "0.7.2"]
                 [metosin/ring-http-response "0.9.4"]
                 [metosin/ring-middleware-format "0.6.0"] ; TODO: replace with newer library
                 [metosin/schema-tools "0.13.1"]
                 [mount "0.1.19"]
                 [net.grey-panther/natural-comparator "1.1"]
                 [net.iakovlev/timeshape "2024a.22"]
                 [org.apache.commons/commons-lang3 "3.17.0"]
                 [org.clojure/clojure "1.12.0"]
                 [org.clojure/data.csv "1.1.0"]
                 [org.clojure/tools.logging "1.3.0"] ; TODO: find out what logging framework we use, move to SLF4J if necessary, preferably something that logs ex-data by default (avoid Timbre, it has a bad track record of slow updates)
                 [org.flywaydb/flyway-core "10.17.3"]
                 [org.flywaydb/flyway-database-postgresql "10.17.3"]
                 [org.locationtech.jts/jts-core "1.20.0"]
                 [org.postgresql/postgresql "42.7.4"]
                 [org.reflections/reflections "0.10.2"]
                 [prismatic/schema "1.4.1"]
                 [ring "1.12.2"]
                 [ring-logger "1.1.1"]
                 [ring-ttl-session "0.3.1"]
                 [ring/ring-defaults "0.5.0"]]
  :managed-dependencies [[cheshire "5.13.0"]
                         [com.cognitect/transit-clj "1.0.333"]
                         [com.fasterxml.jackson.core/jackson-annotations "2.17.2"]
                         [com.fasterxml.jackson.core/jackson-core "2.17.2"]
                         [com.fasterxml.jackson.core/jackson-databind "2.17.2"]
                         [com.fasterxml.jackson.dataformat/jackson-dataformat-cbor "2.17.2"]
                         [com.fasterxml.jackson.dataformat/jackson-dataformat-smile "2.17.2"]
                         [com.fasterxml.jackson.datatype/jackson-datatype-json-org "2.17.2"]
                         [com.fasterxml.jackson.datatype/jackson-datatype-jsr310 "2.17.2"]
                         [commons-codec "1.17.1"]
                         [commons-io "2.16.1"]
                         [org.clojure/core.rrb-vector "0.2.0"]
                         [org.clojure/java.classpath "1.1.0"]
                         [org.clojure/java.jdbc "0.7.12"]
                         [org.clojure/spec.alpha "0.5.238"]
                         [org.clojure/tools.cli "1.1.230"]
                         [org.clojure/tools.reader "1.5.0"]
                         [org.jetbrains.kotlin/kotlin-stdlib "2.0.20"]
                         [org.jetbrains.kotlin/kotlin-stdlib-jdk8 "2.0.20"]
                         [org.slf4j/slf4j-api "2.0.16"]
                         [potemkin "0.4.7"]
                         [ring/ring-codec "1.2.0"]
                         [ring/ring-core "1.12.2"]
                         [seancorfield/next.jdbc "1.2.659"]]
  :pedantic? :warn
  :min-lein-version "2.9.0"

  :source-paths ["src"]
  :java-source-paths ["src-java"]
  :javac-options ["--release" "17"]
  :test-paths ["test"]
  :resource-paths ["resources" "target/web-dist"]
  :target-path "target/%s/"
  :main ^:skip-aot territory-bro.main
  :global-vars {*warn-on-reflection* true
                *print-namespace-maps* false}

  :plugins [[lein-ancient "0.7.0"]
            [lein-pprint "1.3.2"]]

  :aliases {"autotest" ["kaocha" "--watch"]
            "kaocha" ["with-profile" "+kaocha" "run" "-m" "kaocha.runner"]}
  :test-selectors {:default (fn [m] (not (:e2e m)))
                   :e2e :e2e}

  :profiles {:uberjar {:auto-clean false
                       :omit-source true
                       :aot :all
                       :uberjar-name "territory-bro.jar"}

             :dev {:dependencies [[clj-http "3.13.0"]
                                  [criterium "0.4.6"]
                                  [etaoin "1.1.41"]
                                  [lambdaisland/kaocha "1.91.1392"]
                                  [nubank/matcher-combinators "3.9.1"]
                                  [org.clojure/test.check "1.1.1"]
                                  [org.mockito/mockito-core "5.13.0"]
                                  [prismatic/schema-generators "0.1.5"]
                                  [ring/ring-devel "1.12.2"]
                                  [ring/ring-mock "0.4.0"]]
                   :jvm-opts ^:replace ["-Dconf=dev-config.edn"
                                        "-XX:-OmitStackTraceInFastThrow"]
                   :repl-options {:init-ns territory-bro.repl}
                   :resource-paths ["test-resources"]}

             :test [:dev :test0]
             :test0 {:jvm-opts ^:replace ["-Dconf=test-config.edn"
                                          "-XX:-OmitStackTraceInFastThrow"]}
             :kaocha [:test]})
