(ns tx.korma
  (:require [lobos.config :as db]
            [immutant.xa  :as xa])
  (:use korma.db
        korma.core))

(def spec {:subprotocol "h2" :datasource (xa/datasource "lobos" {:adapter "h2" :database "mem:lobos"})})

(defn init []
  (db/create spec)
  (defdb prod spec)

  (defentity authors)
  (defentity posts))

