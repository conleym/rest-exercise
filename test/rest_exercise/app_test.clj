(ns rest-exercise.app-test
  (:require [cheshire.core :as json]
            [clojure.math.combinatorics :as comb]
            [clojure.test :refer :all]
            [ring.mock.request :as mock]
            [rest-exercise.app :refer :all]
            [rest-exercise.testing :refer :all]
            [rest-exercise.entity :as entity]
            [rest-exercise.ring :as r]
            [rest-exercise.storage :as storage]))

;; Clear storage before each deftest.
(use-fixtures :each #((storage/clear) (%)))


(defn- map-with-keys
  [value keys]
  (reduce #(assoc %1 %2 value) {} keys))


(defn- create-expected-map
  [params]
  (update-in params ["number"] entity/canonicalize-number))


(deftest test-with-empty-store
  (testing "Nonsensical URL returns 404 not found."
    (expect-not-found (app (mock/request :get "/xxxxxx"))))
  (testing "Query with empty store returns no results."
    (let [response (app (query-get-req "999"))]))
  (testing "GET of specific resource with empty store returns 404 not found."
    (let [response (app (number-get-req "999" "somecontext"))]
      (expect-not-found response))))


(deftest test-bad-query
  (testing "Query with invalid number returns bad request."
    (let [response (app (query-get-req "$$$"))]
      (expect-bad-request response))))


(deftest test-bad-posts
  (let [all-subsets (comb/subsets ["name" "number" "context"])
        bad-subsets (filter #(< (count % ) 3) all-subsets)]
    (testing "All combinations of missing or empty required form parameters."
      (doseq [param-names all-subsets]
        (let [param-map (map-with-keys "" param-names)]
          (expect-bad-request (app (number-post-req param-map)))
          (expect-bad-request (app (number-post-req param-map :json? true))))))
    (testing "All combinations of form parameters having at least one empty or missing parameter value."
      (doseq [param-names bad-subsets]
        (let [param-map (map-with-keys "999" param-names)]
          (expect-bad-request (app (number-post-req param-map)))
          (expect-bad-request (app (number-post-req param-map :json? true)))))))
  (testing "Non-conforming JSON"
    (expect-bad-request (app (mock/content-type (number-post-req {"name" "some name"
                                                                  "number" "some number"
                                                                  "context" {"not" "valid"}})
                                                json-mime-type))))
  (testing "Invalid JSON"
    (expect-bad-request (app (mock/content-type (simple-number-post-req "{")
                                                json-mime-type)))))


(defn- good-post
  [params expected-url json?]
  (let [response (app (number-post-req params :json? json?))
        response-headers (:headers response)]
    (is (= expected-url
           (get response-headers "Location")))
    response))


(defn- good-post-test
  "Checks the following:

  1. An initial POST with valid parameters returns a response
  containing the expected entity (as JSON) and the expected entity URL
  in the Location header.

  2. A subsequent POST with the same valid parameters returns a 303
  see other with a Location header containing the same expected entity
  URL.

  3. A GET of the expected entity URL returns the entity.

  4. A query using the original number returns a results containing the entity.

  5. A query using the canonicalized number returns same."
  [params expected-map expected-url json?]
  (let [response (good-post params expected-url json?)
        expected-query-result {"results" [expected-map]}
        canonical-number (get expected-map "number")]
    (expect-created response)
    (expect-json-content-type response)
    (is (= expected-map
           (json/parse-string (:body response))))
    (let [response (good-post params expected-url json?)]
      (expect-see-other response))
    (let [response (app (number-get-req canonical-number
                                        (get expected-map "context")))]
      (expect-ok response)
      (expect-json-content-type response)
      (is (= expected-map
             (json/parse-string (:body response)))))
    ;; Test query with canonicalized E.164 number.
    (let [response (app (query-get-req canonical-number))]
      (expect-ok response)
      (expect-json-content-type response)
      (is (= expected-query-result
             (json/parse-string (:body response)))))
    ;; Test query with original number.
    (let [response (app (query-get-req (get params "number")))]
      (expect-ok response)
      (expect-json-content-type response)
      (is (= expected-query-result)
             (json/parse-string (:body response))))))


(let [params {"name" "Some Guy"
              "number" "999"
              "context" "somecontext"}
      expected-map (create-expected-map params)
      expected-json (json/generate-string expected-map)
      expected-url (create-expected-url expected-map)]
  (deftest test-good-form-post
    (testing "Test form posts."
      (good-post-test params expected-map expected-url false)))
  (deftest test-good-json-post
    (testing "Test JSON posts."
      (good-post-test params expected-map expected-url true))))
