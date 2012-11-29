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

(ns immutant.integs.web.noir
  (:use [fntest.core])
  (:use clojure.test)
  (:require [clj-http.client :as client]))

(let [run-tests (not= "1.5.0-beta1"
                      (:full immutant.integs/*current-clojure-version*))]

  (when run-tests
    (use-fixtures :once (with-deployment *file*
                          {
                           :root "target/apps/ring/noir-app/"
                           :context-path "/noir-app"
                           })))

  (deftest simple "it should work"
    (if run-tests
      (let [result (client/get "http://localhost:8080/noir-app/welcome")]
        ;; (println "RESPONSE" result)
        (is (.contains (result :body) "Welcome to noir-app, jim")))
      (println "==> skipping noir tests under 1.5.0 until lobos is fixed. see: https://github.com/budu/lobos/issues/53"))))
