(defproject com.soundcloud/clj-migrate "1.0.1"

  :description "SoundCloud's database migration framework that uses clj files as migrations."
  :url         "https://github.com/soundcloud/clj-migrate"
  :license     {:name "The MIT License"
                :url  "http://opensource.org/licenses/mit-license.php"}

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/tools.cli "0.3.1"]
                 [org.clojure/java.jdbc "0.3.3"]
                 [mysql/mysql-connector-java "5.1.34"]
                 [commons-lang/commons-lang "2.6"]
                 [commons-io/commons-io "2.4"]
                 [clj-time "0.8.0"]]

  :main ^:skip-aot clj-migrate.core

  :min-lein-version "2.4.3"

  :plugins [[lein-deploy-uberjar "2.0.0"]]

  :profiles {:uberjar {:aot :all}})
