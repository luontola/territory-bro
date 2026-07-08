(defproject territory-bro "1.0.0-SNAPSHOT"

  :description "Territory Bro is a tool for managing territory cards in the congregations of Jehovah's Witnesses."
  :url "https://territorybro.com"

  :dependencies [[ch.qos.logback/logback-classic "1.5.37"]
                 [ch.qos.logback/logback-core "1.5.37"]
                 [clj-http "3.13.1"]
                 [com.attendify/schema-refined "0.3.0-alpha4"]
                 [com.auth0/java-jwt "4.5.2"]
                 [com.auth0/jwks-rsa "0.24.1"]
                 [com.auth0/mvc-auth-commons "1.11.1"] ; TODO: upgrade after handling Auth0's extra cookie; skip beta 2.0.0
                 [com.github.seancorfield/next.jdbc "1.3.1118"]
                 [com.layerware/hugsql "0.5.3"]
                 [com.layerware/hugsql-adapter-next-jdbc "0.5.3"]
                 [com.nextjournal/beholder "1.0.3"]
                 [com.vladsch.flexmark/flexmark "0.64.8"]
                 [com.vladsch.flexmark/flexmark-ext-anchorlink "0.64.8"]
                 [cprop "0.1.21"]
                 [enlive "1.1.6"]
                 [hiccup "2.0.0"]
                 [hikari-cp "4.1.0"]
                 [io.nayuki/qrcodegen "1.8.0"]
                 [medley "1.4.0"]
                 [metosin/jsonista "1.0.0"]
                 [metosin/reitit "0.10.1"]
                 [metosin/ring-http-response "0.9.5"]
                 [metosin/ring-middleware-format "0.6.0"] ; TODO: replace with newer library
                 [metosin/schema-tools "0.13.1"]
                 [mount "0.1.24"]
                 [net.grey-panther/natural-comparator "1.1"]
                 [net.iakovlev/timeshape "2026b.29"]
                 [org.apache.commons/commons-lang3 "3.20.0"]
                 [org.apache.poi/poi-ooxml "5.5.1"]
                 [org.clojure/clojure "1.12.5"]
                 [org.clojure/core.cache "1.2.263"]
                 [org.clojure/tools.logging "1.3.1"] ; TODO: find out what logging framework we use, move to SLF4J if necessary, preferably something that logs ex-data by default (avoid Timbre, it has a bad track record of slow updates)
                 [org.flywaydb/flyway-core "12.10.0"]
                 [org.flywaydb/flyway-database-postgresql "12.10.0"]
                 [org.locationtech.jts/jts-core "1.20.0"]
                 [org.openjdk.jol/jol-core "0.17"]
                 [org.postgresql/postgresql "42.7.13"]
                 [org.reflections/reflections "0.10.2"]
                 [prismatic/schema "1.4.1"]
                 [ring "1.15.5"]
                 [ring-logger "1.1.1"]
                 [ring-ttl-session "0.3.1"]
                 [ring/ring-defaults "0.7.1"]]
  :managed-dependencies [[cheshire "6.2.0"]
                         [com.cognitect/transit-clj "1.1.357"]
                         [com.fasterxml.jackson.core/jackson-annotations "2.22"]
                         [com.fasterxml.jackson.core/jackson-core "2.22.1"]
                         [com.fasterxml.jackson.core/jackson-databind "2.22.1"]
                         [com.fasterxml.jackson.dataformat/jackson-dataformat-cbor "2.22.1"]
                         [com.fasterxml.jackson.dataformat/jackson-dataformat-smile "2.22.1"]
                         [com.fasterxml.jackson.datatype/jackson-datatype-json-org "2.22.1"]
                         [com.fasterxml.jackson.datatype/jackson-datatype-jsr310 "2.22.1"]
                         [commons-codec "1.22.0"]
                         [commons-io "2.22.0"]
                         [org.apache.commons/commons-compress "1.28.0"]
                         [org.clojure/core.rrb-vector "0.2.1"]
                         [org.clojure/java.classpath "1.1.1"]
                         [org.clojure/spec.alpha "0.6.249"]
                         [org.clojure/tools.cli "1.4.256"]
                         [org.clojure/tools.reader "1.6.0"]
                         [org.jetbrains.kotlin/kotlin-stdlib "2.4.0"]
                         [org.jetbrains.kotlin/kotlin-stdlib-jdk8 "2.4.0"]
                         [org.slf4j/slf4j-api "2.0.18"]
                         [potemkin "0.4.9"]
                         [ring/ring-codec "1.3.0"]
                         [ring/ring-core "1.15.5"]]
  :pedantic? :warn
  :min-lein-version "2.9.0"

  :source-paths ["src"]
  :java-source-paths ["src-java"]
  :javac-options ["--release" "21"]
  :test-paths ["test"]
  :resource-paths ^:replace ["resources" "target/web-dist"]
  :target-path "target/%s/"
  :main ^:skip-aot territory-bro.main
  :global-vars {*warn-on-reflection* true
                *print-namespace-maps* false}

  :plugins [[lein-ancient "1.0.0"]
            [lein-pprint "1.3.2"]]

  :aliases {"autotest" ["kaocha" "--watch"]
            "kaocha" ["with-profile" "+kaocha" "run" "-m" "kaocha.runner"]}
  :test-selectors {:default (fn [m] (not (:e2e m)))
                   :e2e :e2e}

  :profiles {:uberjar {:auto-clean false
                       :omit-source true
                       :aot :all
                       :uberjar-name "territory-bro.jar"}

             :dev {:dependencies [[com.clojure-goes-fast/clj-async-profiler "1.7.0"]
                                  [criterium "0.4.6"]
                                  [etaoin "1.1.43"]
                                  [lambdaisland/kaocha "1.91.1392"]
                                  [nubank/matcher-combinators "3.10.1"]
                                  [org.clojure/test.check "1.1.3"]
                                  [org.mockito/mockito-core "5.20.0"] ; TODO: upgrade after inline mocks work on Java 25
                                  [prismatic/schema-generators "0.1.5"]
                                  [ring/ring-devel "1.15.5"]
                                  [ring/ring-mock "0.6.2"]]
                   :jvm-opts ^:replace ["-Dconf=dev-config.edn"
                                        "-XX:-OmitStackTraceInFastThrow"
                                        ;; org.openjdk.jol and clj-async-profiler use Java agents
                                        "-Djdk.attach.allowAttachSelf"
                                        "-XX:+EnableDynamicAgentLoading"
                                        "-Dclj-async-profiler.output-dir=./target"]
                   :repl-options {:init-ns territory-bro.repl}
                   :resource-paths ["test-resources"]}

             :test [:dev :test0]
             :test0 {:jvm-opts ^:replace ["-Dconf=test-config.edn"
                                          "-XX:-OmitStackTraceInFastThrow"]}
             :kaocha [:test]})
