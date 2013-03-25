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

(ns immutant.integs.web.pedestal
  (:use fntest.core
        clojure.test
        immutant.integs)
  (:require [clj-http.client :as client]))

(deftest get-hello
  (if (every? (complement version?) [1.3 1.4])
    ((with-deployment "hello"
       {
        :root "target/apps/pedestal/hello"
        :context-path "/"
        })
     (fn []
       (is (= (:body (client/get "http://localhost:8080/"))
              "Hello World!"))
       (is (= (:body (client/get "http://localhost:8080/about"))
              (str "Clojure " (version))))))
    (println "==> skipping pedestal tests since it requires 1.5 or higher")))
