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

(ns test.immutant.jobs.core
  (:require [immutant.registry :as registry])
  (:use immutant.jobs.core
        immutant.test.helpers
        clojure.test
        midje.sweet))

(background (before :facts 
              (do 
                 (registry/put "job-scheduler" nil)
                 (registry/put "singleton-job-scheduler" nil))))

(deffact "non-singleton scheduler lookup should do the right thing"
  (scheduler false) => :scheduler
  (provided
    (registry/fetch "job-scheduler") => :scheduler))

(deffact "singleton scheduler lookup should do the right thing"
  (scheduler true) => :scheduler
  (provided
    (registry/fetch "singleton-job-scheduler") => :scheduler))

(deffact "scheduler creation should occur if the scheduler is not available"
  (scheduler true) => :scheduler
  (scheduler true) => :scheduler
  (against-background
    (create-scheduler true) => :scheduler :times 1))

