;; Copyright 2008-2013 Red Hat, Inc, and individual contributors.
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

(ns immutant.integs.web.web-start
  (:use fntest.core
        clojure.test
        [immutant.integs.integ-helper :only [get-as-data get-as-data*]]))

(use-fixtures :each (with-deployment *file*
                      '{
                        :root "target/apps/ring/basic-ring/"
                        :init 'basic-ring.core/init-web-start-testing
                        :context-path "/basic-ring"
                        }))

(deftest web-start "should work"
  (is (= :basic-ring (:app (get-as-data "/basic-ring/starter")))))

(deftest web-stop "should work"
  (is (= :basic-ring (:app (get-as-data "/basic-ring/starter"))))
  (is (= :basic-ring (:app (get-as-data "/basic-ring/stopper"))))
  (is (= 404 (-> (get-as-data* "/basic-ring/stopper" {:throw-exceptions false}) :result :status)))
  (is (= 404 (-> (get-as-data* "/basic-ring/starter" {:throw-exceptions false}) :result :status))))

(deftest web-start-should-be-idempotent
  (is (nil? (:restarted (get-as-data "/basic-ring/restarter"))))
  (is (:restarted (get-as-data "/basic-ring/restarter"))))
