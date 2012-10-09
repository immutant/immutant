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

(ns immutant.integs.dev
  (:use fntest.core
        clojure.test)
  (:require [clj-http.client :as client]))

(defn run-these-tests? []
  (<= 4 (:minor immutant.integs/*current-clojure-version*)))

(use-fixtures :once (with-deployment *file*
                      '{
                        :root "target/apps/ring/basic-ring/"
                        :init basic-ring.core/init-dev-handler
                        :context-path "/dev"
                        }))

(deftest add-dependencies!
  (if (run-these-tests?)
    (let [result (client/get "http://localhost:8080/dev")
          body (read-string (:body result))]
      ;; (println result)
      (is (= (:final body)
             (set (concat (:original body) (:added body))))))
    (println "Skipping dev tests for" (:full immutant.integs/*current-clojure-version*)
             "since the dev ns only works in clojure >= 1.4.0")))
