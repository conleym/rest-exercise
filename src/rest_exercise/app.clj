(ns rest-exercise.app
  (:require [rest-exercise.storage :as storage]
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
      (let [canonical (storage/canonicalize-number number)
            results (into [] (storage/find-by-number number))]
        (log/info "Query for" number "found" (count results) "results")
        (response {:results (storage/find-by-number number)}))
      (catch NumberParseException e
        (r/bad-request "Parameter 'number' was not a valid phone number.")))
     (r/bad-request "Missing required parameter 'number'.")))


(defn- get
  "Get a single number entry directly."
  [number context]
  (let [result (into [] (storage/find-by-number number))]
    (if (seq result)
      (response (get result 0))
      (r/not-found))))


(defn- post
  [request]
    (try
      (let [new-entry (storage/ensure-entry (:params request))
            ;; Use validated and canonicalized data from new-entry to
            ;; build the URL. Do not use data from the request.
            url-path-elements [number-endpoint-name (:number new-entry) (:context new-entry)]
            url (r/response-url request url-path-elements)]
        (try
          (do
            (storage/add-entry new-entry)
            (created url new-entry))
          ;; Already exists. Point the user to it.
          ;; https://tools.ietf.org/html/rfc7231#section-4.3.3
          (catch SQLIntegrityConstraintViolationException e (redirect url :see-other))))
        (catch NumberParseException e (r/bad-request "Invalid phone number provided."))))


(defroutes app-routes
  (GET "/query" [] query)
  (POST number-endpoint [] post)
  (GET (str number-endpoint "/:number/:context") [number context] (get number context))
  (route/not-found r/not-found))


;; Load data from the csv file.
(storage/init)

(def app
  (-> app-routes
      handler/api
      wrap-json-response
      logger/wrap-with-logger))
