(ns immutant.init
  (:require [immutant.messaging :as msg]
            [clojure.java.io :as io]))

(msg/start "/queue/counter")

(defn inc-file
  [file]
  (let [file (io/file file)]
    (spit file (inc (read-string (slurp file))))))

(defn error
  [_]
  (throw (Exception.)))

(msg/listen "/queue/counter" (comp error inc-file))