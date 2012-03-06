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

(ns org.immutant.core.processors.test.ClojureRuntimeInstaller
  (:use clojure.test)
  (:use immutant.test.as.helpers)
  (:import [org.jboss.as.server.deployment Attachments])
  (:import [org.jboss.modules Module])
  (:import [org.immutant.core.processors ClojureRuntimeInstaller])
  (:import [org.immutant.core ClojureMetaData ClojureRuntime])
  (:require [clojure.java.io :as io]))

(use-fixtures :each
              (harness-with [(ClojureRuntimeInstaller.)]))

;; we need to execute anything that will invoke inside the runtime on
;; a different thread, since the runtime clears threadlocals
(defmacro on-thread [& body]
  `(doto
       (Thread. (fn [] ~@body))
     (.join)))

(deftest it-should-install-a-runtime-if-metadata-is-present
  (on-thread
   (let [phase-context (.createPhaseContext *harness*)
         unit (.getDeploymentUnit phase-context)]
     (doto unit
       (.putAttachment ClojureMetaData/ATTACHMENT_KEY (ClojureMetaData. "foo" {})))

     (.deploy *harness* phase-context)

     (let [runtime (.getAttachment unit ClojureRuntime/ATTACHMENT_KEY)]
       (is (not (nil? runtime)))))))

(deftest it-should-not-install-a-runtime-if-no-metadata-present
  (on-thread
   (let [phase-context (.createPhaseContext *harness*)
         unit (.getDeploymentUnit phase-context)]
     (.deploy *harness* phase-context)
     (is (nil? (.getAttachment unit ClojureRuntime/ATTACHMENT_KEY))))))
