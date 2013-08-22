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

(ns test.immutant.config
  (:use immutant.cache.config
        clojure.test)
  (:import java.util.concurrent.TimeUnit))

(deftest test-lifespan-params
  (is (= (lifespan-params {})                                [-1 TimeUnit/SECONDS -1 TimeUnit/SECONDS]))
  (is (= (lifespan-params {:ttl 42 :idle 7 :units :minutes}) [42 TimeUnit/MINUTES  7 TimeUnit/MINUTES]))
  (is (= (lifespan-params {:ttl 42 :units :hours})           [42 TimeUnit/HOURS   -1 TimeUnit/HOURS]))
  (is (= (lifespan-params {:idle 3})                         [-1 TimeUnit/SECONDS  3 TimeUnit/SECONDS]))
  (is (= (lifespan-params {:ttl [3 :hours] :idle [6 :days]}) [ 3 TimeUnit/HOURS    6 TimeUnit/DAYS]))
  (is (= (lifespan-params {:ttl [60 :minutes]})              [60 TimeUnit/MINUTES -1 TimeUnit/SECONDS]))
  (is (= (lifespan-params {:idle [60 :minutes]})             [-1 TimeUnit/SECONDS 60 TimeUnit/MINUTES]))
  (is (= (lifespan-params {:ttl [1 :day] :idle [1 :hour]})   [ 1 TimeUnit/DAYS     1 TimeUnit/HOURS])))


