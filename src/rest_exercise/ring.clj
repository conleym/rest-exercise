(ns rest-exercise.ring
  (:require [ring.util.codec :as codec]
            [ring.util.http-status :as rstatus]
            [ring.util.response :refer [response response? status]]))


(defn not-found
  "404 handler for the phone number API."
  [& ignored]
  (status (response "") rstatus/not-found))


(defn bad-request
  "400 handler for the phone number API."
  [resp]
  (if (response? resp)
    (status resp rstatus/bad-request)
    (status (response resp) rstatus/bad-request)))


(defn response-url
  "Create a URL, the scheme and host of which are specified by the
  given request, and the path of which contains each path element in
  the order given, properly encoded.

  This is useful for responses requiring, e.g., a Location header."
  [request path-elements]
  (let [encoded-elements (map codec/url-encode path-elements)
        path (apply str (interpose "/" encoded-elements))]
    ;; See also
    ;; https://github.com/ring-clojure/ring/blob/master/ring-core/src/ring/util/request.clj#L5-L11
    ;;
    ;; A production implementation would probably have to account for
    ;; proxy forwarding and adjust scheme and host appropriately.
    (str (-> request :scheme name)
         "://"
         (get-in request [:headers "host"])
         "/"
         path)))
