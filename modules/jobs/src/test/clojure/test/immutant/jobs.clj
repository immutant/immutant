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

(ns test.immutant.jobs
  (:use immutant.jobs
        immutant.jobs.core
        immutant.utilities
        immutant.test.helpers
        clojure.test
        midje.sweet))

(defn fun [])

(deffact "schedule unschedule, create, and register an at-exit handler"
  (schedule "name" "spec" fun) => anything
  (provided
    (unschedule "name") => nil
    (create-job fun "name" "spec" false) => :job
    (at-exit anything) => nil))

