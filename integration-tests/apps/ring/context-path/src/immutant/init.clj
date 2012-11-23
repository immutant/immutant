(ns immutant.init
  (:require [immutant.web :as web])
  (:use context-path.core))

(web/start "/" handler)

(web/start "/subcontext" handler)

(web/start "/subcontext/x2" handler)
