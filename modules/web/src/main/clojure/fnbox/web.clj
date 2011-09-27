(ns fnbox.web
  (:require [ring.util.servlet :as servlet])
  (:require [clojure.string :as str])
  (:import (javax.servlet.http HttpServletRequest
                               HttpServletResponse)))

(defn handler [handler]
  (fn [^HttpServletRequest request
       ^HttpServletResponse response]
    (.setCharacterEncoding response "UTF-8")
    (if-let [response-map (handler (servlet/build-request-map request))]
      (servlet/update-servlet-response response response-map)
      (throw (NullPointerException. "Handler returned nil.")))))

(defn handle-request [app-function request response]
  (let [[namespace function] (map symbol (str/split app-function #"/"))]
       (require namespace)
       ((handler (intern namespace function)) request response)))
