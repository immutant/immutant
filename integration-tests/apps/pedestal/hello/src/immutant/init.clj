(ns immutant.init
  (:require [immutant.web             :as web]
            [io.pedestal.service.http :as bootstrap]
            [hello.service            :as service]))

(web/start-servlet "/" (::bootstrap/servlet (bootstrap/create-servlet service/service)))