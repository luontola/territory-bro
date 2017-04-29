; Copyright © 2015-2017 Esko Luontola
; This software is released under the Apache License 2.0.
; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(defproject territory-bro "0.1.0-SNAPSHOT"

  :description "FIXME: write description"
  :url "http://example.com/FIXME"

  :dependencies [[com.taoensso/timbre "4.10.0"]
                 [compojure "1.5.2"]
                 [conman "0.2.5"]                           ; TODO: figure out how to upgrade, API has changed
                 [environ "1.1.0"]
                 [liberator "0.14.1"]
                 [metosin/ring-http-response "0.8.2"]
                 [metosin/ring-middleware-format "0.6.0"]
                 [migratus "0.9.0"]
                 [org.clojure/clojure "1.8.0"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/tools.nrepl "0.2.13"]         ; TODO: not needed in production?
                 [org.immutant/web "2.1.6"]
                 [org.postgresql/postgresql "42.0.0"]
                 [prone "1.1.4"]
                 [ring "1.5.1" :exclusions [ring/ring-jetty-adapter]]
                 [ring/ring-defaults "0.2.3"]]

  :min-lein-version "2.0.0"
  :uberjar-name "territory-bro.jar"
  :jvm-opts ["-server"]

  :main territory-bro.core
  :migratus {:store :database}

  :plugins [[com.jakemccrary/lein-test-refresh "0.14.0"]
            [lein-ancient "0.6.10"]
            [lein-environ "1.0.1"]
            [migratus-lein "0.2.0"]]
  :profiles
  {:uberjar       {:omit-source true
                   :env         {:production true}
                   :aot         :all}
   :dev           [:project/dev :profiles/dev]
   :test          [:project/test :profiles/test]
   :project/dev   {:dependencies [[mvxcvi/puget "1.0.1"]
                                  [pjstadig/humane-test-output "0.8.1"]
                                  [ring/ring-devel "1.5.1"]
                                  [ring/ring-mock "0.3.0"]]

                   :repl-options {:init-ns territory-bro.core}
                   :injections   [(require 'pjstadig.humane-test-output)
                                  (pjstadig.humane-test-output/activate!)]
                   ;;when :nrepl-port is set the application starts the nREPL server on load
                   :env          {:dev        true
                                  :port       3000
                                  :nrepl-port 7000}}
   :project/test  {:env {:test       true
                         :port       3001
                         :nrepl-port 7001}}
   :profiles/dev  {}
   :profiles/test {}})
