(ns fntest.core
  (:require [fntest.jboss :as jboss]))

(defn with-jboss [f]
  "A test fixture for starting/stopping JBoss"
  (try
    (println "Starting JBoss")
    (jboss/start)
    (if (jboss/wait-for-ready? 20)
      (f)
      (println "JBoss failed to start"))
    (finally
     (println "Stopping JBoss")
     (jboss/stop))))

(defn with-deployment [name descriptor f]
  "A test fixture for deploying/undeploying an app to a running JBoss"
  (try
    (jboss/deploy name descriptor)
    (f)
    (finally
     (jboss/undeploy name))))

