(ns lobos.config
  (:require [immutant.util :as util])
  (:use [lobos [connectivity :only [open-global close-global]]
         [core :only [migrate]]
         [migration :only [*src-directory*]]]))

(defn create [spec]
  (open-global spec)
  (binding [*src-directory* (util/app-relative "src/")]
    (migrate))
  (close-global))
