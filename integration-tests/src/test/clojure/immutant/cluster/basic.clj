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

(ns immutant.cluster.basic
  (:use fntest.core
        clojure.test
        [immutant.cluster.helper :only [get-as-data stop start]]))

(use-fixtures :once (with-deployment *file*
                      '{
                        :root "target/apps/ring/basic-ring/"
                        :init 'basic-ring.core/init-web
                        :context-path "/basic-ring"
                        }))

(deftest bouncing-basic-web
  (is (= :basic-ring (:app (get-as-data "/basic-ring" "server-one"))))
  (is (= :basic-ring (:app (get-as-data "/basic-ring" "server-two"))))
  (stop "server-one")
  (is (thrown? java.net.ConnectException (get-as-data "/basic-ring" "server-one")))
  (start "server-one")
  (is (= :basic-ring (:app (get-as-data "/basic-ring" "server-one")))))

