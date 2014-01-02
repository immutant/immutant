;; Copyright 2008-2014 Red Hat, Inc, and individual contributors.
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
  (:use clojure.test
        immutant.test.as.helpers)
  (:import org.jboss.as.server.deployment.Attachments
           org.jboss.modules.Module
           org.immutant.core.processors.ClojureRuntimeInstaller
           org.immutant.core.ClojureMetaData
           org.immutant.runtime.ClojureRuntimeService)
  (:require [clojure.java.io :as io]))

(use-fixtures :each
              (harness-with [(ClojureRuntimeInstaller.)]))

(deftest it-should-install-a-runtime-if-metadata-is-present
  (on-thread
   (let [phase-context (.createPhaseContext *harness*)
         unit (.getDeploymentUnit phase-context)]
     (doto unit
       (.putAttachment ClojureMetaData/ATTACHMENT_KEY (ClojureMetaData. "foo" {})))

     (.deploy *harness* phase-context)

     (let [runtime (.getAttachment unit ClojureRuntimeService/ATTACHMENT_KEY)]
       (is (not (nil? runtime)))))))

(deftest it-should-not-install-a-runtime-if-no-metadata-present
  (on-thread
   (let [phase-context (.createPhaseContext *harness*)
         unit (.getDeploymentUnit phase-context)]
     (.deploy *harness* phase-context)
     (is (nil? (.getAttachment unit ClojureRuntimeService/ATTACHMENT_KEY))))))
