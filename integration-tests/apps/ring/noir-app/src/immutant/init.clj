(ns immutant.init
  (:require [immutant.web :as web]
            [immutant.util :as util]
            [noir.server :as server]
            [lobos.config]))

;; Use this once the new bultitude and noir 1.3.0 are released
;(server/load-views-ns 'noir-app.views)
(server/load-views (util/app-relative "src/noir_app/views"))
(web/start "/" (server/gen-handler {:mode :dev :ns 'noir-app}))

 
