(ns rest-exercise.app
  "Handlers, routes, and initialization for the phone number webservice."
  (:require [rest-exercise.entity :as entity]
            [rest-exercise.storage :as storage]
            [rest-exercise.ring :as r]
            [clojure.tools.logging :as log]
            [ring.logger :as logger]
            [ring.middleware.json :refer [wrap-json-response]]
            [ring.util.response :refer [response created redirect]]
            [compojure.core :refer :all]
            [compojure.handler :as handler]
            [compojure.route :as route])
  (:import [java.sql SQLIntegrityConstraintViolationException]
           [com.google.i18n.phonenumbers NumberParseException]))


(def ^:const number-endpoint-name "number")
(def ^:const number-endpoint (str "/" number-endpoint-name))


(defn- query
  [request]
  (if-let [number (get-in request [:params :number])]
    ;; Use `into []` to force a vector. It seems cheshire can't
    ;; serialize lazy sequences.
    (try
      (let [canonical (entity/canonicalize-number number)
            results (into [] (storage/find-by-number canonical))]
        (log/info "Query for" number "found" (count results) "results")
        (response {:results results}))
      (catch NumberParseException e
        (r/bad-request "Parameter 'number' was not a valid phone number.")))
     (r/bad-request "Missing required parameter 'number'.")))


(defn- get-entity
  "Get a single entity directly."
  [number context]
  (let [result (into [] (storage/find-by-number-and-context number context))]
    (if (seq result)
      (response (get result 0))
      (r/not-found))))


(defn- post
  [request]
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
        (catch NumberParseException e (r/bad-request "Invalid phone number provided."))))


(defroutes app-routes
  (GET "/query" [] query)
  (POST number-endpoint [] post)
  (GET (str number-endpoint "/:number/:context") [number context] (get-entity number context))
  (route/not-found r/not-found))


;; Load data from the csv file.
(storage/init)

(def app
  (-> app-routes
      handler/api
      wrap-json-response
      logger/wrap-with-logger))
