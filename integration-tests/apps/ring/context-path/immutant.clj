(ns context-path.init
  (:require [immutant.web :as web])
  (:use context-path.core))

(web/start "/" handler)
