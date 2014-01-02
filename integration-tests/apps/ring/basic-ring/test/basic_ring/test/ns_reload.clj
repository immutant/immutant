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

(ns basic-ring.test.ns-reload
  (:use clojure.test)
  (:refer-clojure :exclude [get])
  (:require [basic-ring.core :as core]
            [immutant.web :as web]
            [immutant.util :as util]
            [clj-http.client :as http]))

(defn get []
  (http/get (str (util/app-uri) "/ns-reload")
            {:throw-exceptions false}))

(deftest reload-of-web-ns-should-not-prevent-stop
  (web/stop "/") ;; get the root handler out of the way
  (web/start "/ns-reload" core/handler)
  (is (= 200 (:status (get))))
  (require '[immutant.web :as web] :reload-all)
  (web/stop "/ns-reload")
  (is (= 404 (:status (get)))))
