
(ns testing.hello.service
  (:require [io.pedestal.service.http :as http]
            [io.pedestal.service.http.route.definition :refer [defroutes]]
            [ring.util.response :as ring-resp]))

(defn home-page [request] (ring-resp/response "Hello World!"))

(defroutes routes [[["/" {:get home-page}]]])

(def service {::http/routes routes})

(def servlet (::http/servlet (http/create-servlet service)))
