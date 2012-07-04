(ns noir-app.init
  (:require [immutant.web :as web]
            [immutant.utilities :as util]
            [noir.server :as server]))

(server/load-views (util/app-relative "src/noir_app/views"))
(web/start "/" (server/gen-handler {:mode :dev :ns 'noir-app}))

 