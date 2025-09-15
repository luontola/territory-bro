(defproject territory-bro "1.0.0-SNAPSHOT"

  :description "Territory Bro is a tool for managing territory cards in the congregations of Jehovah's Witnesses."
  :url "https://territorybro.com"

  :dependencies [[ch.qos.logback/logback-classic "1.5.18"]
                 [ch.qos.logback/logback-core "1.5.18"]
                 [clj-http "3.13.1"]
                 [com.attendify/schema-refined "0.3.0-alpha4"]
                 [com.auth0/java-jwt "4.5.0"]
                 [com.auth0/jwks-rsa "0.23.0"]
                 [com.auth0/mvc-auth-commons "1.11.1"]
                 [com.github.seancorfield/next.jdbc "1.3.1070"]
                 [com.layerware/hugsql "0.5.3"]
                 [com.layerware/hugsql-adapter-next-jdbc "0.5.3"]
                 [com.nextjournal/beholder "1.0.3"]
                 [com.vladsch.flexmark/flexmark "0.64.8"]
                 [com.vladsch.flexmark/flexmark-ext-anchorlink "0.64.8"]
                 [cprop "0.1.20"]
                 [enlive "1.1.6"]
                 [hiccup "2.0.0"]
                 [hikari-cp "3.3.0"]
                 [io.nayuki/qrcodegen "1.8.0"]
                 [medley "1.4.0"]
                 [metosin/jsonista "0.3.13"]
                 [metosin/reitit "0.9.1"]
                 [metosin/ring-http-response "0.9.5"]
                 [metosin/ring-middleware-format "0.6.0"] ; TODO: replace with newer library
                 [metosin/schema-tools "0.13.1"]
                 [mount "0.1.23"]
                 [net.grey-panther/natural-comparator "1.1"]
                 [net.iakovlev/timeshape "2025b.28"]
                 [org.apache.commons/commons-lang3 "3.18.0"]
                 [org.apache.poi/poi-ooxml "5.4.1"]
                 [org.clojure/clojure "1.12.2"]
                 [org.clojure/core.cache "1.1.234"]
                 [org.clojure/tools.logging "1.3.0"] ; TODO: find out what logging framework we use, move to SLF4J if necessary, preferably something that logs ex-data by default (avoid Timbre, it has a bad track record of slow updates)
                 [org.flywaydb/flyway-core "11.12.0"]
                 [org.flywaydb/flyway-database-postgresql "11.12.0"]
                 [org.locationtech.jts/jts-core "1.20.0"]
                 [org.openjdk.jol/jol-core "0.17"]
                 [org.postgresql/postgresql "42.7.7"]
                 [org.reflections/reflections "0.10.2"]
                 [prismatic/schema "1.4.1"]
                 [ring "1.15.1"]
                 [ring-logger "1.1.1"]
                 [ring-ttl-session "0.3.1"]
                 [ring/ring-defaults "0.7.0"]]
  :managed-dependencies [[cheshire "6.1.0"]
                         [com.cognitect/transit-clj "1.0.333"]
                         [com.fasterxml.jackson.core/jackson-annotations "2.20"]
                         [com.fasterxml.jackson.core/jackson-core "2.20.0"]
                         [com.fasterxml.jackson.core/jackson-databind "2.20.0"]
                         [com.fasterxml.jackson.dataformat/jackson-dataformat-cbor "2.20.0"]
                         [com.fasterxml.jackson.dataformat/jackson-dataformat-smile "2.20.0"]
                         [com.fasterxml.jackson.datatype/jackson-datatype-json-org "2.20.0"]
                         [com.fasterxml.jackson.datatype/jackson-datatype-jsr310 "2.20.0"]
                         [commons-codec "1.19.0"]
                         [commons-io "2.20.0"]
                         [org.apache.commons/commons-compress "1.28.0"]
                         [org.clojure/core.rrb-vector "0.2.0"]
                         [org.clojure/java.classpath "1.1.0"]
                         [org.clojure/spec.alpha "0.5.238"]
                         [org.clojure/tools.cli "1.1.230"]
                         [org.clojure/tools.reader "1.5.2"]
                         [org.jetbrains.kotlin/kotlin-stdlib "2.2.20"]
                         [org.jetbrains.kotlin/kotlin-stdlib-jdk8 "2.2.20"]
                         [org.slf4j/slf4j-api "2.0.17"]
                         [potemkin "0.4.8"]
                         [ring/ring-codec "1.3.0"]
                         [ring/ring-core "1.15.1"]]
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

             :dev {:dependencies [[com.clojure-goes-fast/clj-async-profiler "1.6.2"]
                                  [criterium "0.4.6"]
                                  [etaoin "1.1.43"]
                                  [lambdaisland/kaocha "1.91.1392"]
                                  [nubank/matcher-combinators "3.9.2"]
                                  [org.clojure/test.check "1.1.1"]
                                  [org.mockito/mockito-core "5.19.0"]
                                  [prismatic/schema-generators "0.1.5"]
                                  [ring/ring-devel "1.15.1"]
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
