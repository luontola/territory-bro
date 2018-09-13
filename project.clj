; Copyright © 2015-2018 Esko Luontola
; This software is released under the Apache License 2.0.
; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(defproject territory-bro "1.0.0-SNAPSHOT"

  :description "Territory Bro is a tool for managing territory cards in the congregations of Jehovah's Witnesses."
  :url "https://territorybro.com"

  :dependencies [[com.taoensso/timbre "4.10.0"]
                 [compojure "1.6.1"]
                 [conman "0.8.2"]
                 [environ "1.1.0"]
                 [liberator "0.15.2"]
                 [metosin/ring-http-response "0.9.0"]
                 [metosin/ring-middleware-format "0.6.0"]
                 [migratus "1.0.9"]
                 [org.clojure/clojure "1.9.0"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/tools.nrepl "0.2.13"] ; TODO: remove, not needed in production?
                 [org.immutant/web "2.1.10"]
                 [org.postgresql/postgresql "42.2.5"]
                 [prone "1.6.0"]
                 [ring "1.7.0" :exclusions [ring/ring-jetty-adapter]]
                 [ring/ring-defaults "0.3.2"]]

  :min-lein-version "2.0.0"
  :uberjar-name "territory-bro.jar"
  :jvm-opts ["-server"]

  :main territory-bro.core
  :migratus {:store :database}

  :plugins [[com.jakemccrary/lein-test-refresh "0.14.0"]
            [lein-ancient "0.6.15"]
            [lein-environ "1.0.1"]
            [migratus-lein "0.5.7"]]
  :profiles
  {:uberjar {:omit-source true
             :env {:production true}
             :aot :all}
   :dev [:project/dev :profiles/dev]
   :test [:project/test :profiles/test]
   :project/dev {:dependencies [[mvxcvi/puget "1.0.2"]
                                [pjstadig/humane-test-output "0.8.3"]
                                [ring/ring-devel "1.7.0"]
                                [ring/ring-mock "0.3.2"]]

                 :repl-options {:init-ns territory-bro.core}
                 :injections [(require 'pjstadig.humane-test-output)
                              (pjstadig.humane-test-output/activate!)]
                 ;;when :nrepl-port is set the application starts the nREPL server on load
                 :env {:dev true
                       :port 3000
                       :nrepl-port 7000}}
   :project/test {:env {:test true
                        :port 3001
                        :nrepl-port 7001}}
   :profiles/dev {}
   :profiles/test {}})
