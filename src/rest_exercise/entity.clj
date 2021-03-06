(ns rest-exercise.entity
  "Definition of the PhoneNumber entity upon which this service
  operates. Includes validation and creation functions."
  (:import [com.google.i18n.phonenumbers PhoneNumberUtil PhoneNumberUtil$PhoneNumberFormat]))


(def ^:private ^:const default-region-code "US")


;; type representing an entry in the storage system.
(defrecord PhoneNumber [name number context]
  Object
  (toString
    [_]
    (str "{name: '" name
         "' number: '" number
         "' context: '" context "'}")))


(defn canonicalize-number
  "Parse a phone number and convert to an E.164 string.
  Throws com.google.i18n.phonenumbers.NumberParseException if the
  number is invalid."
  [phone-number-str]
  (let [pnu (PhoneNumberUtil/getInstance)
        parsed (.parse pnu phone-number-str default-region-code)]
    (.format pnu parsed PhoneNumberUtil$PhoneNumberFormat/E164)))


(defn seq-to-entity
  "Create a new storage entry representing the data in the sequence.

  The sequence is expected to contain (in order): number, context, and
  name."
  [[number context name]]
  (PhoneNumber. name (canonicalize-number number) context))


(defn entity?
  "Boolean entity test. true iff the argument is an entity."
  [arg]
  (instance? PhoneNumber arg))


(defn to-entity
  "Convert maps and sequences to entities.

  For convenience, entities are also accepted and simply returned."
  [arg]
  (if (entity? arg) arg)
  (if (seq? arg) (seq-to-entity arg))
  (if (map? arg) (seq-to-entity [(:number arg) (:context arg) (:name arg)])))
