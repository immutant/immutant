(ns org.fnbox.core.processors.test.ClojureRuntimeInstaller
  (:use clojure.test)
  (:use fnbox.test.as.helpers)
  (:import [org.jboss.as.server.deployment Attachments])
  (:import [org.jboss.modules Module])
  (:import [org.fnbox.core.processors ClojureRuntimeInstaller])
  (:import [org.fnbox.core ClojureMetaData ClojureRuntime])
  (:require [clojure.java.io :as io]))

(use-fixtures :each
              (harness-with [(ClojureRuntimeInstaller.)]))

(deftest it-should-install-a-runtime-if-metadata-is-present
  (let [phase-context (.createPhaseContext *harness*)
        unit (.getDeploymentUnit phase-context)
        module (Module/getSystemModule)]
    (doto unit
      (.putAttachment ClojureMetaData/ATTACHMENT_KEY (ClojureMetaData. "foo" {}))
      (.putAttachment Attachments/MODULE module))
    
    (.deploy *harness* phase-context)
    
    (let [runtime (.getAttachment unit ClojureRuntime/ATTACHMENT_KEY)]
      (is (not (nil? runtime)))
      (is (= (.getClassLoader module) (.getClassLoader runtime))))))

(deftest it-should-not-install-a-runtime-if-no-metadata-present
  (let [phase-context (.createPhaseContext *harness*)
        unit (.getDeploymentUnit phase-context)]
    (.deploy *harness* phase-context)
    (is (nil? (.getAttachment unit ClojureRuntime/ATTACHMENT_KEY)))))
