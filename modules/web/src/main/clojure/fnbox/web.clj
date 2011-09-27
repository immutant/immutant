(ns fnbox.web
  (:use ring.util.servlet)
  (:import (javax.servlet.http HttpServletRequest
                               HttpServletResponse)))

(defn handler [handler]
  (fn [^HttpServletRequest request
       ^HttpServletResponse response]
    (.setCharacterEncoding response "UTF-8")
    (if-let [response-map (handler (build-request-map request))]
      (update-servlet-response response response-map)
      (throw (NullPointerException. "Handler returned nil.")))))

(defn handle-request [namespace app-function request response]
  ((handler (intern (symbol namespace) (symbol app-function))) request response))
