(ns immutant.web)

(defn start* [& args]
  (reset! immutant.runtime-test/a-value args))
