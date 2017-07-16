(ns rest-exercise.app
  (:require [rest-exercise.storage :as storage]
            [rest-exercise.ring :as r]
            [clojure.tools.logging :as log]
            [ring.logger :as logger]
            [ring.middleware.json :refer [wrap-json-response]]
            [ring.util.response :refer [response created redirect]]
            [compojure.core :refer :all]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [ring.util.response :as resp])
  (:import [java.io FileNotFoundException]
             [java.sql SQLIntegrityConstraintViolationException]))


(def ^:const number-endpoint-name "number")
(def ^:const number-endpoint (str "/" number-endpoint-name))


(defn- query
  [request]
  (if-let [number (get-in request [:params :number])]
    ;; Use `into []` to force a vector. It seems cheshire can't
    ;; serialize lazy sequences.
    (let [results (into [] (storage/find-by-number number))]
     (log/info "Query for" number "found" (count results) "results")
     (response {:results (storage/find-by-number number)}))
    (r/bad-request "Missing required parameter 'number'.")))


(defn- get-number
  [number]
  (if-let [result (storage/find-by-number number)]
    (response (get result 0))
    (r/not-found)))


(defn- post
  [request]
  (let [params (:params request)
        url (r/response-url request [number-endpoint-name (:number params)])]
    (try
      (created url (storage/add-entry params))
      ;; Already exists. Point the user to it.
      (catch SQLIntegrityConstraintViolationException e (redirect url :see-other)))))


(defroutes app-routes
  (GET "/query" [] query)
  (POST number-endpoint [] post)
  (GET (str number-endpoint "/:number") [number] (get-number number))
  (route/not-found r/not-found))


;; Load data from the csv file.
(storage/init)

(def app
  (-> app-routes
      handler/api
      wrap-json-response
      logger/wrap-with-logger))
