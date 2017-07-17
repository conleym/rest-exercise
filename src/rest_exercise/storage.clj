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
  [[number context name]]
  (PhoneNumberEntry. name number context))


(defn ensure-entry
  [arg]
  (if (instance? PhoneNumberEntry arg) arg)
  (if (seq? arg) (seq-to-entry arg))
  (if (map? arg) (seq-to-entry [(:number arg) (:context arg) (:name arg)])))


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

;; In-memory database table (set of PhoneNumberEntries) and unique
;; index (set of PhoneNumberUniqueIndexEntries).
(def ^:private state (ref {:table #{}
                           :index #{}}))

;; has init been accomplished? used to avoid re-init on hot-reload.
(def ^:private initialized (ref nil))


(defn- constraint-violated-by?
  [new-index-entry]
  (contains? (:index @state) new-index-entry))


(defn- maybe-throw
  [throw-on-violations new-index-entry]
  (if throw-on-violations
    (throw (SQLIntegrityConstraintViolationException.
            (str "Unique constraint violation: " new-index-entry)))
    (log/warn "Unique constraint violation: " new-index-entry)))


(defn- add-entry-impl
  "Add a PhoneNumberEntry to storage.
  Throws java.sql.SQLIntegrityConstraintViolationException if the
  unique index constraint is violated.
  "
  ([new-entry]
   (add-entry-impl new-entry true))
  ([new-entry throw-on-violations]
  (dosync
   (let [new-index-entry (index-entry-for new-entry)]
     ;; Add an entry only if it does not violate the uniqueness constraint.
     (if (constraint-violated-by? new-index-entry)
       (maybe-throw throw-on-violations new-index-entry)
       (let [new-state {:table (conj (:table @state) new-entry)
                        :index (conj (:index @state) new-index-entry)}]
         (ref-set state new-state)))
     new-entry))))



(defn add-entry
  "Add a new entry to storage.
  Accepts any of the following:
  - A PhoneNumberEntry instance
  - A map containing :name, :number, and :context keys.
  - A sequence of [number, context, name].

  Throws SQLIntegrityConstraintViolationException if the number and
  context already appear in an entry in storage.
"
  ([entry]
   (add-entry-impl (ensure-entry entry))))


(defn find-by-number
  "Get a lazy sequence of entries matching the given phone number."
  [number]
  (filter #(= (:number %) number) (:table @state)))

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


(defn- load-add-entry
  [entry]
  (add-entry-impl entry false))


(defn- load-csv-record
  "Load a single csv record into storage."
  [csv-record]
  (->> csv-record
       seq-to-entry
       load-add-entry))


(defn- load-csv-records
  "Load a sequence of csv records (each a sequence of values) into storage."
  [csv-records]
  (doall
   (map load-csv-record csv-records)))


(defn init
  "Initialize the storage subsystem."
  []
  (if-not @initialized
    (do
     (log/info "Initializing storage subsystem.")
     (let [csv-file (resource "interview-callerid-data.csv")]
       (with-open [reader (io/reader csv-file :encoding "UTF-8")]
         (load-csv-records (csv/read-csv reader))))
     (dosync (ref-set initialized true))
     (log/info "Initialization complete. Added" (count (:table @state)) "entries."))))
