(ns fnbox.test.as.helpers
  (:import [org.fnbox.test.as DeployerTestHarness]))

(def ^:dynamic *harness*)

(defn harness-with [deployers]
  (fn [test-f]
    (binding [*harness* (DeployerTestHarness.)]
      (doall (map #(.appendDeployer *harness* %) deployers))
      (test-f)
      (.closeAllContexts *harness*))))
