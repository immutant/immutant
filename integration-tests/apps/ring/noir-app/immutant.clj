(ns noir-app.init
  (:require [immutant.web :as web]
            [immutant.utilities :as util]
            [clojure.java.io :as io]
            [noir.server :as server]))

(server/load-views (io/file (util/app-root) "src/noir_app/views"))
(web/start "/" (server/gen-handler {:mode :dev :ns 'noir-app}))
