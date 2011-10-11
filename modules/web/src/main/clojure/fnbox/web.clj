(ns fnbox.web
  (:require [ring.util.servlet :as servlet])
  (:require [clojure.string :as str])
  (:require [fnbox.utilities :as util])
  (:import (javax.servlet.http HttpServletRequest
                               HttpServletResponse)))

(defn handler [app]
  (fn [^HttpServletRequest request
       ^HttpServletResponse response]
    (.setCharacterEncoding response "UTF-8")
    (if-let [response-map (app (servlet/build-request-map request))]
      (servlet/update-servlet-response response response-map)
      (throw (NullPointerException. "Handler returned nil.")))))

(defn handle-request [app-function request response]
  ((handler (util/load-and-invoke app-function)) request response))
