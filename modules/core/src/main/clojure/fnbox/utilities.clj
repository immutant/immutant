(ns fnbox.utilities
  (:require [clojure.string :as str]))

(defn load-and-invoke [namespaced-fn & args]
  (let [[namespace function] (map symbol (str/split namespaced-fn #"/"))]
    (require namespace)
    (apply (intern namespace function) args)))
