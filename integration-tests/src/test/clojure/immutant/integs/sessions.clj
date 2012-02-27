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


(def cookies (atom {}))

(use-fixtures :once (with-deployment *file*
                      '{
                        :root "target/apps/ring/sessions/"
                        :init sessions.core/init-all
                        :context-path "/sessions"
                        }))

(use-fixtures :each (fn [f]
                      (reset! cookies {})
                      (f)))

(defn get-with-cookies [sub-path query-string]
  (when-let [result (client/get
                     (str "http://localhost:8080/sessions/" sub-path "?" query-string)
                     {:cookies @cookies})]
    ;; (println "RESULT:" result)
    (if (get-in result [:cookies "JSESSIONID"])
      (reset! cookies (:cookies result)))
    (read-string (:body result))))

(deftest basic-immutant-session-test
  (are [expected query-string] (= expected (get-with-cookies "immutant" query-string))
       {"ham" "biscuit"}                    "ham=biscuit"
       {"ham" "biscuit"}                    ""
       {"ham" "biscuit", "biscuit" "gravy"} "biscuit=gravy"
       {"ham" "biscuit", "biscuit" "gravy"} ""))

(deftest immutant-session-should-only-have-a-jsessionid-cookie
  (get-with-cookies "immutant" "ham=biscuit")
  (is (= #{"JSESSIONID" "a-cookie"} (set (keys @cookies)))))

(deftest basic-ring-session-test
  (are [expected query-string] (= expected (get-with-cookies "ring" query-string))
       {"ham" "biscuit"}                    "ham=biscuit"
       {"ham" "biscuit"}                    ""
       {"ham" "biscuit", "biscuit" "gravy"} "biscuit=gravy"
       {"ham" "biscuit", "biscuit" "gravy"} ""))

(deftest ring-session-should-have-a-ring-session-cookie
  (get-with-cookies "ring" "ham=biscuit")
  (is (= #{"ring-session" "a-cookie" "JSESSIONID"} (set (keys @cookies)))))
