(ns lobos.config
  (:require [immutant.util :as util])
  (:use [lobos [connectivity :only [open-global]]
         [core :only [migrate]]
         [migration :only [*src-directory*]]]))

(def db {:classname "org.h2.Driver"
         :subprotocol "h2"
         :subname "mem:foo"})

(open-global db)

(binding [*src-directory* (util/app-relative "src/")]
  (migrate))
