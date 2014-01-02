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

(ns immutant.integs.web.sessions
  (:use fntest.core
        clojure.test
        [immutant.integs.integ-helper :only [get-as-data get-as-data*]]))


(def cookies (atom {}))

(use-fixtures :once (with-deployment *file*
                      '{
                        :root "target/apps/ring/sessions/"
                        :init 'sessions.core/init-all
                        :context-path "/sessions"
                        }))

(use-fixtures :each (fn [f]
                      (reset! cookies {})
                      (f)))

(defn get-with-cookies [sub-path  & [query-string]]
  (let [{:keys [result body]} (get-as-data*
                               (str "/sessions/" sub-path "?" query-string)
                               {:cookies @cookies})]
    ;; (println "RESULT:" result)
    (if (some #{"JSESSIONID" "ring-session"} (keys (:cookies result)))
      (reset! cookies (:cookies result)))
    body))

(deftest basic-immutant-session-test
  (are [expected query-string] (= expected (get-with-cookies "immutant" query-string))
       {"ham" "biscuit"}                    "ham=biscuit"
       {"ham" "biscuit"}                    ""
       {"ham" "biscuit", "biscuit" "gravy"} "biscuit=gravy"
       {"ham" "biscuit", "biscuit" "gravy"} ""))

(deftest session-clearing-for-immutant-sessions
  (is (= {"ham" "biscuit"} (get-with-cookies "immutant" "ham=biscuit")))
  (is (not (seq (get-with-cookies "clear"))))
  (is (not (seq (get-with-cookies "immutant")))))

(deftest immutant-session-should-also-have-a-ring-session-cookie
   (get-with-cookies "immutant" "ham=biscuit")
   (is (= #{"JSESSIONID" "a-cookie" "ring-session"} (set (keys @cookies)))))

(deftest session-clearing-for-immutant-sessions-using-jsessionid
  (is (= {"ham" "biscuit"} (get-with-cookies "immutant-jsessionid" "ham=biscuit")))
  (is (not (seq (get-with-cookies "clear-jsessionid"))))
  (is (not (seq (get-with-cookies "immutant-jsessionid")))))

(deftest immutant-session-with-jssessionid-name-should-not-have-a-ring-session-cookie
   (get-with-cookies "immutant-jsessionid" "ham=biscuit")
   (is (= #{"JSESSIONID" "a-cookie"} (set (keys @cookies)))))

(deftest basic-ring-session-test
  (are [expected query-string] (= expected (get-with-cookies "ring" query-string))
       {"ham" "biscuit"}                    "ham=biscuit"
       {"ham" "biscuit"}                    ""
       {"ham" "biscuit", "biscuit" "gravy"} "biscuit=gravy"
       {"ham" "biscuit", "biscuit" "gravy"} ""))

(deftest ring-session-should-have-a-ring-session-cookie
  (get-with-cookies "ring" "ham=biscuit")
  (is (= #{"ring-session" "a-cookie"} (set (keys @cookies)))))

(deftest session-clearing-for-ring-sessions
  (is (= {"biscuit" "gravy"} (get-with-cookies "ring" "biscuit=gravy")))
  (is (not (seq (get-with-cookies "clear-ring"))))
  (is (not (seq (get-with-cookies "ring")))))
