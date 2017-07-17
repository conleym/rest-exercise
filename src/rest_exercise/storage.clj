(ns rest-exercise.storage
  (:require [rest-exercise.entity :as entity]
            [clojure.tools.logging :as log]
            [clojure.data.csv :as csv]
            [clojure.java.io :as io])
  (:import [java.io FileNotFoundException]
           [java.sql SQLIntegrityConstraintViolationException]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Types
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;; type representing an entry in the storage system's unique index.
(defrecord PhoneNumberUniqueIndexEntry [number context]
  Object
  (toString
    [_]
    (str "{number: '" number
         "' context: '" context "'}")))


(defn- create-index-entry
  "Create an index entry for a given PhoneNumber entity."
  [entity]
  (PhoneNumberUniqueIndexEntry. (:number entity) (:context entity)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; State
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; In-memory database table (set of PhoneNumbers) and unique
;; index (map of PhoneNumberUniqueIndexEntries to corresponding
;; PhoneNumbers).
(def ^:private state (ref {:table #{}
                           :index {}}))

;; has init been accomplished? used to avoid re-init on hot-reload.
(def ^:private initialized (ref nil))


(defn- index-lookup
  [index-entry]
  (get (:index @state) index-entry))


(defn find-by-number-and-context
  [number context]
  (index-lookup (PhoneNumberUniqueIndexEntry. number context)))


(defn- maybe-throw
  [throw-on-violations new-entity old-entity]
  (let [msg (str "Unique constraint violation: "
                 new-entity
                 " conflicts with "
                 old-entity)]
    (if throw-on-violations
      (throw (SQLIntegrityConstraintViolationException. msg))
      (log/warn msg))))


(defn add-entity
  "Add a PhoneNumber entity to storage.
  Throws java.sql.SQLIntegrityConstraintViolationException if the
  unique index constraint is violated."
  ([new-entity]
   (add-entity new-entity true))
  ([new-entity throw-on-violations]
  (dosync
   (let [new-index-entry (create-index-entry new-entity)
         old-entity (index-lookup new-index-entry)]
     ;; Add a phone number only if it does not violate the uniqueness constraint.
     (if-not (nil? old-entity)
       (maybe-throw throw-on-violations new-entity old-entity)
       (let [new-state {:table (conj (:table @state) new-entity)
                        :index (assoc (:index @state) new-index-entry new-entity)}]
         (ref-set state new-state)))
     new-entity))))


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


(defn- load-add-entity
  [entity]
  (add-entity entity false))


(defn- load-csv-record
  "Load a single csv record into storage."
  [csv-record]
  (->> csv-record
       entity/seq-to-entity
       load-add-entity))


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
