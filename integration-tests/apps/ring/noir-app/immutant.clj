(ns noir-app.init
  (:require [immutant.web :as web]
            [immutant.util :as util]
            [noir.server :as server]
            [lobos.config]))

(server/load-views (util/app-relative "src/noir_app/views"))
(web/start "/" (server/gen-handler {:mode :dev :ns 'noir-app}))

 
