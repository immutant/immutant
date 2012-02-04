;; Copyright 2008-2012 Red Hat, Inc, and individual contributors.
;; 
;; This is free software; you can redistribute it and/or modify it
;; under the terms of the GNU Lesser General Public License as
;; published by the Free Software Foundation; either version 2.1 of
;; the License, or (at your option) any later version.
;; 
;; This software is distributed in the hope that it will be useful,
;; but WITHOUT ANY WARRANTY; without even the implied warranty of
;; MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
;; Lesser General Public License for more details.
;; 
;; You should have received a copy of the GNU Lesser General Public
;; License along with this software; if not, write to the Free
;; Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
;; 02110-1301 USA, or see the FSF site: http://www.fsf.org.

(ns org.immutant.core.processors.test.ApplicationInitializer
  (:use clojure.test)
  (:use immutant.test.as.helpers)
  (:import [org.immutant.core.processors ApplicationInitializer])
  (:import [org.immutant.core ClojureMetaData ClojureRuntime]))

(def a-value (atom "not-called"))

(use-fixtures :each
              (harness-with [(ApplicationInitializer.)]))

(deftest it-should-call-the-initialize-function-with-the-init-function
  (let [phase-context (.createPhaseContext *harness*)
        unit (.getDeploymentUnit phase-context)
        func-name "a.namespace/init"]
    (doto unit
      (.putAttachment ClojureRuntime/ATTACHMENT_KEY
                      (proxy [ClojureRuntime] [(.getClassLoader (class ClojureRuntime)) "app-name"]
                        (invoke [initialize-fn args]
                          (reset! a-value [initialize-fn (first args)]))))
      (.putAttachment ClojureMetaData/ATTACHMENT_KEY (ClojureMetaData. "foo" {"init" func-name})))
    
    (.deploy *harness* phase-context)

    (is (= ["immutant.runtime/initialize" func-name] @a-value))))


