(ns noir-app.init
  (:require [immutant.web :as web]
            [noir.server :as server]))

(server/load-views (str (web/src-dir) "/noir_app/views"))
(web/start "/" (server/gen-handler {:mode :dev :ns 'noir-app}))
