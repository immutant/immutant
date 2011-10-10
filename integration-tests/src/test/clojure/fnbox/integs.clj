(ns fnbox.integs
  (:use [clojure.test])
  (:use [fntest.core])
  (:use [clojure.tools.namespace :only (find-namespaces-in-dir)])
  (:require [clojure.java.io :as io]))

(let [integs (io/file (.getParentFile (io/file *file*)) "integs")
      namespaces (find-namespaces-in-dir integs)]
  (apply require namespaces)
  (when-not *compile-files*
    (let [results (atom [])]
      (let [report-orig report]
        (binding [fntest.jboss/home "../build/assembly/target/stage/fnbox/jboss"
                  report (fn [x] (report-orig x)
                           (swap! results conj (:type x)))]
          (with-jboss #(apply run-tests namespaces))))
      (shutdown-agents)
      (System/exit (if (empty? (filter {:fail :error} @results)) 0 -1)))))

