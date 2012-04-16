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

(ns org.immutant.core.processors.test.DeploymentDescriptorParsingProcessor
  (:use clojure.test
        immutant.test.helpers
        immutant.test.as.helpers)
  (:import org.immutant.core.processors.DeploymentDescriptorParsingProcessor
           org.immutant.core.ClojureMetaData
           org.jboss.as.server.deployment.DeploymentUnitProcessingException)
  (:require [clojure.java.io :as io]))

(use-fixtures :each
              (harness-with [(DeploymentDescriptorParsingProcessor.)]))

(deftest it-should-raise-with-no-root-specified
  (is (thrown? DeploymentUnitProcessingException
               (.deployResourceAs *harness* (io/resource "simple-descriptor.clj") "app.clj" ))))

(deftest it-should-raise-with-an-invalid-root-specified
  (is (thrown? DeploymentUnitProcessingException
               (.deployResourceAs *harness* (io/resource "invalid-root-descriptor.clj") "app.clj" ))))

(deftest it-should-create-metadata-when-given-a-valid-root
  (let [unit (.deployResourceAs *harness* (io/resource "valid-root-descriptor.clj") "app.clj" )]
    (is-not (nil? (.getAttachment unit ClojureMetaData/ATTACHMENT_KEY)))))

(deftest it-should-populate-the-metadata
  (let [unit (.deployResourceAs *harness* (io/resource "valid-root-descriptor.clj") "app.clj" )
        metadata (.getAttachment unit ClojureMetaData/ATTACHMENT_KEY)]
    (are [exp val-method] (= exp (val-method metadata))
         (io/file "/tmp/")      .getRoot
         "my.namespace/init"    .getInitFunction
         "app"                  .getApplicationName)))

