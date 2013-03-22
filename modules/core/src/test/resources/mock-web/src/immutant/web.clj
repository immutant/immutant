(ns immutant.web)

(defn start-handler [& args]
  (reset! immutant.runtime-test/a-value args))
