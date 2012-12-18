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

(ns immutant.integs.jobs
  (:use fntest.core
        clojure.test
        immutant.integs.integ-helper))

(use-fixtures :once (with-deployment *file*
                      {
                       :root "target/apps/jobs/"
                       }))

(defn get-values
  ([]
     (get-values ""))
  ([query]
     (get-as-data (str "/jobs?" query))))

(deftest simple "it should work"
  (let [initial-value (:a-value (get-values))]
    (Thread/sleep 1000)
    (let [next-value (:a-value (get-values))]
      (is (> next-value initial-value))
      (Thread/sleep 1000)
      (is (> (:a-value (get-values)) next-value)))))

(deftest rescheduling
  (is (> (:another-value (get-values)) 0))
  (get-values "reschedule")
  (Thread/sleep 2000)
  (is (= (:another-value (get-values)) "rescheduled")))

(deftest unschedule
  (get-values "unschedule")
  (Thread/sleep 1000)
  (let [initial-value (:a-value (get-values))]
    (Thread/sleep 10000)
    (is (= initial-value (:a-value (get-values))))))

(deftest a-job-should-be-using-the-deployment-classloader
  (is (re-find deployment-class-loader-regex
              (:loader (get-values)))))
