;; SoundCloud's database migration framework that uses clj files as migrations.
;;
;; The MIT License (MIT)
;;
;; Copyright (c) 2015 SoundCloud
;;
;; Permission is hereby granted, free of charge, to any person obtaining a copy
;; of this software and associated documentation files (the "Software"), to deal
;; in the Software without restriction, including without limitation the rights
;; to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
;; copies of the Software, and to permit persons to whom the Software is
;; furnished to do so, subject to the following conditions:
;;
;; The above copyright notice and this permission notice shall be included in
;; all copies or substantial portions of the Software.
;;
;; THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
;; IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
;; FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
;; AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
;; LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
;; OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
;; THE SOFTWARE.
;;
(ns clj-migrate.core
  (:import
    (java.net URLEncoder)
    (com.mysql.jdbc Driver)
    (org.apache.commons.lang StringUtils)
    (org.apache.commons.io FilenameUtils FileUtils))
  (:require
    [clojure.java.io :as io]
    [clojure.tools.cli :refer [parse-opts]]
    [clojure.string :as string]
    [clojure.java.jdbc :as sql]
    [clj-time.format :as fmt]
    [clj-time.core :as time])
  (:gen-class))

(def migration-table-name "migrations")

(def migration-dir-name "migrations")

(defn url-encode [value]
  (URLEncoder/encode value "UTF-8"))

(defn encode-database-url [jdbc-url username password]
  (str jdbc-url "?user=" (url-encode username) "&password=" (url-encode password)))

(defn dbspec [url] {:connection-uri url})

(def created-formatter (fmt/formatters :date-hour-minute-second-ms))

(def filename-formatter (fmt/formatter "yyyyMMddHHmmss"))

(defn down? [direction] (= :down direction))

(defn migration-files [migrations-dir direction]
  (let [files (->> (io/file migrations-dir) .listFiles (map str) sort (filter #(.endsWith % ".clj")))]
    (if (down? direction) (reverse files) files)))

(defn filepath-to-name-space-string [path]
  (let [ns-str (-> (StringUtils/substringAfter path "/") (string/replace ".clj" "") (string/replace "/" ".") (string/replace "_" "-"))]
    (if (Character/isDigit (get ns-str 0)) (str "migration-" ns-str) ns-str)))

(defn migration-id [path]
  (-> (FilenameUtils/getBaseName path) (string/replace "_" "-")))

(defn run-migration-function [path direction db]
  (load-file path)
  (let [fn-name (str (filepath-to-name-space-string path) "/" (name direction))
        fn-var (resolve (symbol fn-name))]
    (println "Running" fn-name)
    (apply fn-var [db])))

(defn should-migrate? [id db migration-table direction]
  (let [decision (if (down? direction) (complement empty?) empty?)]
    (decision (sql/query db [(str "select id from " migration-table " where id = ?") id]))))

(defn record-migration [id db migration-table direction]
  (if (down? direction)
    (sql/delete! db (symbol migration-table) ["id = ?" id])
    (sql/insert! db (symbol migration-table) {:id id :created_at (fmt/unparse created-formatter (time/now))})))

(defn run-migrations [db migrations-dir migration-table direction]
  (println "Running database migrations")
  (doseq [path (migration-files migrations-dir direction)]
    (let [id (migration-id path)]
      (when (should-migrate? id db migration-table direction)
        (run-migration-function path direction db)
        (record-migration id db migration-table direction)))))

(defn check-migrations-table [db migration-table]
  (println "Checking that" migration-table "table exists")
  (sql/db-do-commands db (str "CREATE TABLE IF NOT EXISTS " migration-table " (id varchar(255) DEFAULT NULL, created_at varchar(32) DEFAULT NULL)")))

(defn check-database-exists [database-name db]
  (println "Checking that" database-name "database exists")
  (sql/db-do-commands db (str "CREATE DATABASE IF NOT EXISTS " database-name)))

(defn migrate
  "Run the database migration clj files in the migrations-dir using the
   provided database configuration and direction (:up or :down)."
  ([cfg]
    (migrate cfg migration-dir-name))
  ([cfg migrations-dir]
    (migrate cfg migrations-dir :up))
  ([cfg migrations-dir direction]
    (let [jdbc-url (:url cfg)
          username (:username cfg)
          password (:password cfg)
          migration-table (get cfg :table migration-table-name)
          database-name (StringUtils/substringAfterLast jdbc-url "/")
          update-db (dbspec (encode-database-url jdbc-url username password))
          create-db (dbspec (encode-database-url (StringUtils/substringBeforeLast jdbc-url "/") username password))]
      (check-database-exists database-name create-db)
      (check-migrations-table update-db migration-table)
      (run-migrations update-db migrations-dir migration-table direction)
      (println "Done"))))

(defn create-contents [ns-str]
  (str "(ns " ns-str "\n"
       "  (:require [clojure.java.jdbc :as j]))\n\n"
       "(defn up [db])\n\n"
       "(defn down [db])\n"))

(defn create
  "Create a skeleton migration file in the migrations-dir. You can do this by hand
   but then you'd have to create the full timestamped file name and namespace."
  [migrations-dir name]
  (let [filename (string/lower-case (string/replace name #"[\s-]+" "_"))
        path (io/file migrations-dir (str (fmt/unparse filename-formatter (time/now)) "_" filename ".clj"))
        ns-str (filepath-to-name-space-string (str path))]
    (FileUtils/write path (create-contents ns-str) "UTF-8")
    (println "Created" (str path))))

(def cli-options [["-j" "--url JDBC_URL" "Database connection URL"]
                  ["-u" "--username USERNAME" "Database connection username"]
                  ["-p" "--password PASSWORD" "Database connection password"]
                  ["-d" "--dir DIRECTORY" "Directory on classpath with clj migrations" :default migration-dir-name]
                  ["-t" "--table NAME" "Migration table name" :default migration-table-name]
                  ["-h" "--help"]])

(defn usage [options-summary]
  (->> [""
        "SoundCloud's database migration framework that uses clj files as migrations."
        ""
        "Usage: java clj_migrate.core [options] action [create migration name]"
        ""
        "Options:"
        options-summary
        ""
        "Actions:"
        "  create         Create a skeleton for a db migration"
        "  migrate-up     Run all the up migrations"
        "  migrate-down   Run all the down migrations"
        ""]
       (string/join \newline)))

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (string/join \newline errors)))

(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn -main [& args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    (cond
      (:help options) (exit 0 (usage summary))
      (not (> (count arguments) 0)) (exit 1 (usage summary))
      errors (exit 1 (error-msg errors)))
    (let [migrations-dir (get options :dir migration-dir-name)]
      (case (first arguments)
        "migrate-up" (migrate options migrations-dir :up)
        "migrate-down" (migrate options migrations-dir :down)
        "create" (create migrations-dir (string/join "_" (rest arguments)))
        (exit 1 (usage summary))))))
