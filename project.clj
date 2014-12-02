(defproject pilaf "0.1.1-SNAPSHOT"
  :description "Pilaf: extensions and helper utilities for Korma"
  :url "http://github.com/bbbates/pilaf"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[clj-time "0.8.0"]
                 [korma "0.4.0" :scope "provided"]]
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.6.0"]]}})
