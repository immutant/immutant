(ns fntest.core
  (:require [fntest.jboss :as jboss]))

(defn with-jboss [f]
  "A test fixture for starting/stopping JBoss"
  (try
    (println "Starting JBoss")
    (jboss/start)
    (f)
    (finally
     (println "Stopping JBoss")
     (jboss/stop))))

(defn with-deployment [name descriptor]
  "Returns a test fixture for deploying/undeploying an app to a running JBoss"
  (fn [f]
    (try
      (when (jboss/wait-for-ready? 20) (jboss/deploy name descriptor))
      (f)
      (finally
       (jboss/undeploy name)))))

