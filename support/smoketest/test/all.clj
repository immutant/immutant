(ns all
  (:require immutant.web
            immutant.messaging
            immutant.cache
            immutant.jobs
            immutant.daemons
            immutant.xa)
  (:use clojure.test))

(deftest everything "Confirm all namespaces load when not running inside Immutant"
  (is true "everything loaded"))
