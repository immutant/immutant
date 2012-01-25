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

(ns immutant.integs.sessions
  (:use [fntest.core])
  (:use clojure.test)
  (:require [clj-http.client :as client]))

(use-fixtures :each (with-deployment *file*
                      {
                       :root "apps/ring/basic-ring/"
                       :init "basic-ring.core/init-web-sessions"
                       :context-path "/basic-ring"
                       }))

(def cookies (atom {}))

(defn get-with-cookies
  ([]
     (get-with-cookies ""))
  ([query-string]
     (when-let [result (client/get
                        (str "http://localhost:8080/basic-ring/sessions?" query-string)
                        {:cookies @cookies})]
       ;; (println "RESULT:" result)
       (if (contains? result :cookies)
         (reset! cookies (:cookies result)))
       (read-string (:body result)))))

(deftest basic-session-test
  (is (= {"ham" "biscuit"}                    (get-with-cookies "ham=biscuit")))
  (is (= {"ham" "biscuit"}                    (get-with-cookies)))
  (is (= {"ham" "biscuit", "biscuit" "gravy"} (get-with-cookies "biscuit=gravy")))
  (is (= {"ham" "biscuit", "biscuit" "gravy"} (get-with-cookies))))
