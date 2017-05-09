(defproject realworld-server-tester "0.1.0-SNAPSHOT"
  :description "Tool for testing a realworld project backend implementation"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [clj-http "3.5.0"]
                 [org.clojure/data.json "0.2.6"]
                 [slingshot "0.12.2"]
                 [clansi "1.0.0"]
                 [com.github.javafaker/javafaker "0.13"]
                 [clj-time "0.13.0"]]
  :plugins [[lein-cljfmt "0.5.6"]
            [lein-kibit "0.1.2"]]
  :aot [realworld-server-tester.core]
  :main realworld-server-tester.core)
