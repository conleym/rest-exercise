(defproject rest-exercise "0.1.0-SNAPSHOT"
  :description "Very simple JSON phone number lookup service"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/tools.logging "0.4.0"]
                 [org.clojure/data.csv "0.1.4"]
                 [compojure "1.6.0"]
                 ;; ring.middleware.logger seems to be
                 ;; abandonware. Let's try this. Exclude old
                 ;; tools.logging.
                 [ring-logger "0.7.7" :exclusions [org.clojure/tools.logging]]
                 ;; HTTP status code constants
                 [metosin/ring-http-response "0.9.0"]
                 ;; JSON serialization (uses old ring-core)
                 [ring/ring-json "0.4.0" :exclusions [ring.ring-core]]]

  :plugins [[lein-ring "0.12.0"]]
  :ring {:handler rest-exercise.app/app
         :nrepl {:start? yes :port 4567}}
  :main rest-exercise.storage
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}
             :dev {:dependencies [[ring/ring-mock "0.3.1"]]}})
