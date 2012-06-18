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

(ns fntest.core
  (:require [fntest.jboss :as jboss]))

(defn with-jboss
  "A test fixture for starting/stopping JBoss"
  [f]
  (try
    (println "Starting JBoss")
    (jboss/start)
    (f)
    (finally
     (println "Stopping JBoss")
     (jboss/stop))))

(defn with-deployments
  "Returns a test fixture for deploying/undeploying multiple apss to a running JBoss"
  [descriptor-map]
  (fn [f]
    (try
      (when (jboss/wait-for-ready? 20)
        (jboss/deploy descriptor-map))
      (f)
      (finally
       (apply jboss/undeploy (keys descriptor-map))))))

(defn with-deployment
  "Returns a test fixture for deploying/undeploying an app to a running JBoss"
  [name descriptor-or-file]
  (with-deployments {name descriptor-or-file}))
