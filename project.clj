;; Copyright © 2015-2023 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(defproject territory-bro "1.0.0-SNAPSHOT"

  :description "Territory Bro is a tool for managing territory cards in the congregations of Jehovah's Witnesses."
  :url "https://territorybro.com"

  :dependencies [[camel-snake-kebab "0.4.2"]
                 [clj-http "3.12.3"]
                 [com.attendify/schema-refined "0.3.0-alpha4"]
                 [com.auth0/java-jwt "4.4.0"]
                 [com.auth0/jwks-rsa "0.22.0"]
                 [com.layerware/hugsql "0.5.1"]
                 [compojure "1.6.2"]
                 [conman "0.8.4" :upgrade false] ; TODO: 0.8.5 and higher fail with: java.lang.IllegalArgumentException: No implementation of method: :get-level of protocol: #'clojure.java.jdbc/Connectable found for class: com.zaxxer.hikari.HikariDataSource
                 [cprop "0.1.17"]
                 [liberator "0.15.3"]
                 [luminus-immutant "0.2.5"] ; TODO: replace with ring-jetty
                 [medley "1.3.0"]
                 [metosin/jsonista "0.2.7"]
                 [metosin/ring-http-response "0.9.1"]
                 [metosin/ring-middleware-format "0.6.0"] ; TODO: replace with newer library
                 [metosin/schema-tools "0.12.2"]
                 [mount "0.1.16"]
                 [org.apache.commons/commons-lang3 "3.11"]
                 [org.clojars.luontola/ns-tracker "0.3.1-patch1"]
                 [org.clojure/clojure "1.10.1"]
                 [org.clojure/data.csv "1.0.1"]
                 [org.clojure/tools.logging "1.1.0"] ; TODO: find out what logging framework we use, move to SLF4J if necessary, preferably something that logs ex-data by default (avoid Timbre, it has a bad track record of slow updates)
                 [org.flywaydb/flyway-core "7.3.0"]
                 [org.postgresql/postgresql "42.2.18"]
                 [prismatic/schema "1.1.12"]
                 [ring-logger "1.0.1"]
                 [ring-ttl-session "0.3.1"]
                 [ring/ring-defaults "0.3.2"]]
  :managed-dependencies [[ch.qos.logback/logback-classic "1.2.3"]
                         [ch.qos.logback/logback-core "1.2.3"]
                         [cheshire "5.10.0"]
                         [com.fasterxml.jackson.core/jackson-annotations "2.12.0"]
                         [com.fasterxml.jackson.core/jackson-core "2.12.0"]
                         [com.fasterxml.jackson.core/jackson-databind "2.12.0"]
                         [com.fasterxml.jackson.dataformat/jackson-dataformat-cbor "2.12.0"]
                         [com.fasterxml.jackson.dataformat/jackson-dataformat-smile "2.12.0"]
                         [com.fasterxml.jackson.datatype/jackson-datatype-json-org "2.12.0"]
                         [com.fasterxml.jackson.datatype/jackson-datatype-jsr310 "2.12.0"]
                         [commons-codec "1.15"]
                         [hikari-cp "2.13.0"]
                         [org.clojure/java.classpath "1.0.0"]
                         [org.clojure/java.jdbc "0.7.11"]
                         [org.clojure/spec.alpha "0.2.187"]
                         [org.clojure/tools.reader "1.3.4"]
                         [org.slf4j/slf4j-api "1.7.30"]
                         [ring "1.8.2"]
                         [ring/ring-codec "1.1.2"]
                         [ring/ring-core "1.8.2"]
                         [ring/ring-jetty-adapter "1.8.2"]
                         [ring/ring-servlet "1.8.2"]
                         [seancorfield/next.jdbc "1.1.613"]]
  :exclusions [ns-tracker]
  :pedantic? :warn
  :min-lein-version "2.9.0"

  :source-paths ["src"]
  :java-source-paths ["src-java"]
  :javac-options ["--release" "17"]
  :test-paths ["test"]
  :resource-paths ["resources"]
  :target-path "target/%s/"
  :main ^:skip-aot territory-bro.main
  :global-vars {*warn-on-reflection* true
                *print-namespace-maps* false}

  :plugins [[lein-ancient "0.6.15"]
            [lein-pprint "1.3.2"]]

  :aliases {"autotest" ["kaocha" "--watch"]
            "kaocha" ["with-profile" "+kaocha" "run" "-m" "kaocha.runner"]}

  :profiles {:uberjar {:omit-source true
                       :aot :all
                       :uberjar-name "territory-bro.jar"}

             :dev {:dependencies [[com.github.kyleburton/clj-xpath "1.4.11"]
                                  [lambdaisland/kaocha "1.0.732"]
                                  [org.clojure/test.check "1.1.0"]
                                  [prismatic/schema-generators "0.1.3"]
                                  [ring/ring-devel "1.8.2"]
                                  [ring/ring-mock "0.4.0"]]
                   :jvm-opts ^:replace ["-Dconf=dev-config.edn"
                                        "-XX:-OmitStackTraceInFastThrow"]
                   :repl-options {:init-ns territory-bro.main}}

             :test [:dev :test0]
             :test0 {:jvm-opts ^:replace ["-Dconf=test-config.edn"
                                          "-XX:-OmitStackTraceInFastThrow"]}
             :kaocha [:test]})
