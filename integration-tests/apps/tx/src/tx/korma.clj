(ns tx.korma
  (:require lobos.config
            tx.core)
  (:use korma.db
        korma.core))

(defdb prod {:datasource @tx.core/h2})

(defentity authors)
(defentity posts)

