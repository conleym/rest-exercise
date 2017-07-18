(ns rest-exercise.app
  "Handlers, routes, and initialization for the phone number webservice."
  (:require [rest-exercise.entity :as entity]
            [rest-exercise.storage :as storage]
            [rest-exercise.ring :as r]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [ring.middleware.defaults :as defaults]
            [ring.logger :as logger]
            [ring.middleware.json :refer [wrap-json-response wrap-json-params]]
            [ring.util.response :refer [response created redirect]]
            [compojure.core :refer :all]
            [compojure.route :as route])
  (:import [java.sql SQLIntegrityConstraintViolationException]
           [com.google.i18n.phonenumbers NumberParseException]))


(def ^:const number-endpoint-name "number")
(def ^:const number-endpoint (str "/" number-endpoint-name))


(defn- query
  [request]
  (if-let [number (get-in request [:params :number])]
    (try
      (let [canonical (entity/canonicalize-number number)
            ;; Use `into []` to force a vector. It seems cheshire can't
            ;; serialize lazy sequences.
            results (vec (storage/find-by-number canonical))]
        (log/info "Query for" number "found" (count results) "results")
        (response {:results results}))
      (catch NumberParseException e
        (r/bad-request "Parameter 'number' was not a valid phone number.")))
    (r/bad-request "Missing required parameter 'number'.")))


(defn- get-entity
  "Get a single entity directly."
  [number context]
  (if-let [result (storage/find-by-number-and-context number context)]
    (response result)
    (r/not-found)))


(defn- validate-post-params
  "Given a parameter map, produce a list of missing required
  parameter names for POST requests."
  [params]
  ;; params keys are keywords. Convert list of keywords with blank
  ;; values to a list of strings to make an intelligable message for
  ;; clients.
  (let [missing-kws (filter #(str/blank? (get params %)) [:name :number :context])]
    (vec (map #(str "'" (name %) "'") missing-kws))))


(defn- post
  [request]
  (let [params (:params request)
        validation-result (validate-post-params params)]
    (log/info "POST request with params " params ". Validation result: " validation-result)
    (if (seq validation-result)
      (r/bad-request (str "The following required parameters were not supplied or were blank: "
                          (str/join "," validation-result)))
      (try
        (let [new-entity (entity/to-entity (:params request))
              ;; Use validated and canonicalized data from new-entity to
              ;; build the URL of the new entity. Do not use data from
              ;; the request.
              url-path-elements [number-endpoint-name (:number new-entity) (:context new-entity)]
              url (r/response-url request url-path-elements)]
          (try
            (do
              (storage/add-entity new-entity)
              (created url new-entity))
            ;; Already exists. Point the user to it, as suggested by
            ;; https://tools.ietf.org/html/rfc7231#section-4.3.3
            (catch SQLIntegrityConstraintViolationException e (redirect url :see-other))))
        (catch NumberParseException e (r/bad-request "Invalid 'number' provided."))))))


(defroutes app-routes
  (GET "/query" [] query)
  (POST number-endpoint [] post)
  (GET (str number-endpoint "/:number/:context") [number context] (get-entity number context))
  (route/not-found r/not-found))


;; This exists to make potential customization/experimentation with
;; ring-defaults wrappers easier, and provides a point for possible
;; configuration with, e.g., environ.
;;
;; We could, for example, use environ to figure out whether to use the
;; secure or insecure defaults.
(defn- wrap-api
  "Wrap a handler using ring-defaults."
  [handler]
  (defaults/wrap-defaults handler defaults/api-defaults))


(def app
  (-> app-routes
      wrap-api
      wrap-json-response
      wrap-json-params
      logger/wrap-with-logger))
