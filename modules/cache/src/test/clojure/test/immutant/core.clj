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

(ns test.immutant.core
  (:use immutant.cache.core
        clojure.test)
  (:import org.infinispan.configuration.cache.CacheMode))

(deftest builder-defaults-to-local-mode
  (let [config (.build (builder {}))]
    (is (= CacheMode/LOCAL (.. config clustering cacheMode)))))

(deftest builder-defaults-to-dist-sync-mode-when-clustered
  (with-redefs [clustered? (constantly true)]
    (let [config (.build (builder {}))]
      (is (= CacheMode/DIST_SYNC (.. config clustering cacheMode))))))

(deftest builder-bombs-with-invalid-cache-mode
  (with-redefs [clustered? (constantly true)]
    (is (thrown? IllegalArgumentException (builder {:mode :wrong})))))
