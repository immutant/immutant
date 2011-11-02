(ns basic-ring.init
  (:require [immutant.web :as web])
  (:use basic-ring.core))

(web/start "/" another-handler)
