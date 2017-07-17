(ns rest-exercise.storage
  (:require [clojure.tools.logging :as log]
            [clojure.data.csv :as csv]
            [clojure.java.io :as io])
  (:import [java.io FileNotFoundException]
           [java.sql SQLIntegrityConstraintViolationException]
           [com.google.i18n.phonenumbers PhoneNumberUtil PhoneNumberUtil$PhoneNumberFormat]))

(def ^:private ^:const default-region-code "US")


(defn canonicalize-number
  "Parse a phone number and convert to an E.164 string.

  Throws com.google.i18n.phonenumbers.NumberFormatException if the
  number is invalid."
  [phone-number-str]
  (let [pnu (PhoneNumberUtil/getInstance)
        parsed (.parse pnu phone-number-str default-region-code)]
    (.format pnu parsed PhoneNumberUtil$PhoneNumberFormat/E164)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Types
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; type representing an entry in the storage system.
(defrecord PhoneNumberEntry [name number context]
  Object
  (toString
    [_]
    (str "{name: '" name
         "' number: '" number
         "' context: '" context "'}")))


(defn- seq-to-entry
  "Create a new storage entry representing the data in the sequence."
  [[number context name]]
  (PhoneNumberEntry. name (canonicalize-number number) context))


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
    (str "{number: '" number
         "' context: '" context "'}")))


(defn- index-entry-for
  "Create an index entry for a given PhoneNumberEntry."
  [entry]
  (PhoneNumberUniqueIndexEntry. (:number entry) (:context entry)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; State
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; In-memory database table (set of PhoneNumberEntries) and unique
;; index (map of PhoneNumberUniqueIndexEntries to corresponding
;; PhoneNumberEntries).
(def ^:private state (ref {:table #{}
                           :index {}}))

;; has init been accomplished? used to avoid re-init on hot-reload.
(def ^:private initialized (ref nil))


(defn- index-lookup-by-entry
  [new-index-entry]
  (get (:index @state) new-index-entry))


(defn find-by-number-and-context
  [number context]
  (index-lookup-by-entry (PhoneNumberUniqueIndexEntry. number context)))


(defn- maybe-throw
  [throw-on-violations new-entry old-entry]
  (let [msg (str "Unique constraint violation: "
                 new-entry
                 " conflicts with "
                 old-entry)]
    (if throw-on-violations
      (throw (SQLIntegrityConstraintViolationException. msg))
      (log/warn msg))))


(defn add-entry
  "Add a PhoneNumberEntry to storage.
  Throws java.sql.SQLIntegrityConstraintViolationException if the
  unique index constraint is violated."
  ([new-entry]
   (add-entry new-entry true))
  ([new-entry throw-on-violations]
  (dosync
   (let [new-index-entry (index-entry-for new-entry)
         old-entry (index-lookup-by-entry new-index-entry)]
     ;; Add an entry only if it does not violate the uniqueness constraint.
     (if-not (nil? old-entry)
       (maybe-throw throw-on-violations new-entry old-entry)
       (let [new-state {:table (conj (:table @state) new-entry)
                        :index (assoc (:index @state) new-index-entry new-entry)}]
         (ref-set state new-state)))
     new-entry))))


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
  (add-entry entry false))


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
