(ns rest-exercise.testing
  "App testing utilities."
  (:require [rest-exercise.app :refer [number-endpoint query-endpoint]]
            [cheshire.core :as json]
            [clojure.test :refer :all]
            [ring.mock.request :as mock]
            [ring.util.http-status :as rstatus]
            [rest-exercise.ring :as r]))


;; Fake base URLs for testing.
(def ^:private ^:constant app-base-url "http://app.local:8081")
(def ^:private number-endpoint-url (str app-base-url number-endpoint))


;; As defined in ring-json. Using a MIME type parsing library rather
;; than testing for equality would be better, but for this simple
;; exercise, this will do.
(def ^:constant json-mime-type "application/json; charset=utf-8")


(defn expect-json-content-type
  [response]
  (is (= json-mime-type
         (get-in response [:headers "Content-Type"]))))


(defn expect-status
  "Assert that an HTTP response has the expected status."
  [response status]
  (is (= status (:status response))))


(defn expect-bad-request
  [response]
  (expect-status response rstatus/bad-request))


(defn expect-created
  [response]
  (expect-status response rstatus/created))


(defn expect-not-found
  [response]
  (expect-status response rstatus/not-found))


(defn expect-ok
  [response]
  (expect-status response rstatus/ok))


(defn expect-see-other
  [response]
  (expect-status response rstatus/see-other))


(defn simple-number-post-req
  "Post argument to the number endpoint."
  [params]
  (mock/request :post number-endpoint-url params))


(defn number-post-req
  "Post form parameters or JSON to the number endpoint."
  [params & {:keys [json?]
             :or {json? false}}]
  (if json?
    (mock/content-type (simple-number-post-req (json/generate-string params))
                       json-mime-type)
    (simple-number-post-req params)))


(defn create-expected-url
  "Create the URL we expect to see in the Location header in a valid
  POST response, given a map containing the values expected in the
  parsed JSON response to said POST."
  [expected-map]
  (str number-endpoint-url "/"
       (get expected-map "number") "/"
       (get expected-map "context")))


(defn query-get-req
  [number]
  (mock/request :get (str app-base-url query-endpoint) {"number" number}))


(defn number-get-req
  [number context]
  (mock/request :get (str number-endpoint-url "/" number "/" context)))


(def empty-results
  {:results []})


(defn expect-empty-query-results
  [response]
  (expect-ok response)
  (expect-json-content-type)
  (is (= empty-results (json/parse-string (:body response)))))
