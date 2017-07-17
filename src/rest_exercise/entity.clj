(ns rest-exercise.entity
  "Definition of the PhoneNumber entity upon which this service
  operates. Includes validation and creation functions."
  (:import [com.google.i18n.phonenumbers PhoneNumberUtil PhoneNumberUtil$PhoneNumberFormat]))


(def ^:private ^:const default-region-code "US")


;; type representing an entry in the storage system.
(defrecord PhoneNumberEntry [name number context]
  Object
  (toString
    [_]
    (str "{name: '" name
         "' number: '" number
         "' context: '" context "'}")))


(defn canonicalize-number
  "Parse a phone number and convert to an E.164 string.
  Throws com.google.i18n.phonenumbers.NumberFormatException if the
  number is invalid."
  [phone-number-str]
  (let [pnu (PhoneNumberUtil/getInstance)
        parsed (.parse pnu phone-number-str default-region-code)]
    (.format pnu parsed PhoneNumberUtil$PhoneNumberFormat/E164)))


(defn seq-to-entry
  "Create a new storage entry representing the data in the sequence."
  [[number context name]]
  (PhoneNumberEntry. name (canonicalize-number number) context))


(defn ensure-entry
  [arg]
  (if (instance? PhoneNumberEntry arg) arg)
  (if (seq? arg) (seq-to-entry arg))
  (if (map? arg) (seq-to-entry [(:number arg) (:context arg) (:name arg)])))
