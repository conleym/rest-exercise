(ns rest-exercise.storage
  (:require [clojure.tools.logging :as log]
            [clojure.data.csv :as csv]
            [clojure.java.io :as io])
  (:import [java.io FileNotFoundException]
           [java.sql SQLIntegrityConstraintViolationException]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Types
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; type representing an entry in the storage system.
(defrecord PhoneNumberEntry [name number context]
  Object
  (toString
    [_]
    (str "name: " name
         " number: " number
         " context: " context)))

(defn- seq-to-entry
  "Create a new storage entry representing the data in the sequence."
  [args]
  (let [[number context name] args]
    (PhoneNumberEntry. name number context)))


(defn ensure-entry
  [arg]
  (if (instance? PhoneNumberEntry arg) arg)
  (if (seq? arg) (seq-to-entry arg))
  (if (map? arg) (seq-to-entry [(:name arg) (:number arg) (:context arg)])))


;; type representing an entry in the storage system's unique index.
(defrecord PhoneNumberUniqueIndexEntry [number context]
  Object
  (toString
    [_]
    (str "number: " number
         " context: " context)))


(defn- index-entry-for
  "Create an index entry for a given PhoneNumberEntry."
  [entry]
  (PhoneNumberUniqueIndexEntry. (:number entry) (:context entry)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; State
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; set of entries
(def ^:private table (ref #{}))

;; set of name/context pairs, used to simulate a unique index
;; on a database table.
(def ^:private unique-index (ref (hash-set)))


(defn- add-entry-impl
  "Add a PhoneNumberEntry to storage.
  Throws java.sql.SQLIntegrityConstraintViolationException if the
  unique index constraint is violated.
  "
  ([entry]
   (add-entry-impl entry true))
  ([entry throw-on-violations]
  (dosync
   (let [index-entry (index-entry-for entry)]
     (if (contains? @unique-index index-entry)
       (if throw-on-violations
         (throw (SQLIntegrityConstraintViolationException. (str "Unique constraint violation: " index-entry)))
         (log/warn "Unique constraint violation: " index-entry)))
     (ref-set table (conj @table entry))
     (ref-set unique-index (conj @unique-index index-entry))
     entry))))



(defn add-entry
  ([entry]
   (add-entry-impl (ensure-entry entry))))


(defn find-by-number
  "Get a lazy sequence of entries matching the given phone number."
  [number]
  (filter #(= (:number %) number) @table))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; CSV initialization
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- resource
  "Open a resource on the classpath.
   Throws FileNotFoundException if the file doesn't exist."
  [filename]
  (if-let [resource (io/resource filename)]
    resource
    (throw (FileNotFoundException. (str filename " on classpath")))))


(defn- asdf [entry] (add-entry-impl entry false))


(defn- load-csv-record
  "Load a single csv record into storage."
  [csv-record]
  (->> csv-record
       seq-to-entry
       asdf))


(defn- load-csv-records
  "Load a sequence of csv records (each a sequence of values) into storage."
  [csv-records]
  (doall
   (map load-csv-record csv-records)))


(defn init
  "Initialize the storage subsystem."
  []
  (log/info "Initializing storage subsystem.")
  (let [csv-file (resource "interview-callerid-data.csv")]
    (with-open [reader (io/reader csv-file :encoding "UTF-8")]
      (load-csv-records (csv/read-csv reader))))
  (log/info "Initialization complete. Added" (count @table) "entries."))
