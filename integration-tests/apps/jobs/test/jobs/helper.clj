(ns jobs.helper
  (:require [immutant.messaging :as msg])
  (:import java.util.UUID))

(defn random-queue []
  (let [q (-> (UUID/randomUUID) str msg/as-queue)]
    (msg/start q)
    q))

