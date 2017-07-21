(ns rest-exercise.ring
  "Ring utilities."
  (:require [clojure.string :as str]
            [ring.util.codec :as codec]
            [ring.util.http-status :as rstatus]
            [ring.util.response :refer [response response? status content-type]]))


(def ^:const ^:private err-mime-type "text/plain")


(defn not-found
  "404 handler for the phone number API."
  [& ignored]
  (content-type (status (response "") rstatus/not-found) err-mime-type))


(defn bad-request
  "400 handler for the phone number API."
  [resp]
  (content-type
   (if (response? resp)
     (status resp rstatus/bad-request)
     (status (response resp) rstatus/bad-request))
   err-mime-type))


(defn response-url
  "Create a URL, the scheme and host of which are specified by the
  given request, and the path of which contains each path element in
  the order given, properly encoded.

  This is useful for responses requiring, e.g., a Location header."
  [request path-elements]
  (let [encoded-elements (map codec/url-encode path-elements)
        path (str/join "/" encoded-elements)]
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
