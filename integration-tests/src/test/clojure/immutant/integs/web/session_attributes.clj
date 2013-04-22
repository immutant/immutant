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

(ns immutant.integs.web.session-attributes
  (:use fntest.core
        clojure.test
        [immutant.integs.integ-helper :only [get-as-data get-as-data*]]
        [ring.util.codec :only [url-encode]]))


(def cookies (atom {}))

(use-fixtures :once (with-deployment *file*
                      '{
                        :root "target/apps/ring/sessions/"
                        :init 'sessions.core/init-session-attrs
                        :context-path "/sessions"
                        }))

(use-fixtures :each (fn [f]
                      (reset! cookies {})
                      (f)))

(defn get-with-cookies [& [query-string]]
  (let [{:keys [result body]} (get-as-data*
                               (str "/sessions/session-attrs?" query-string)
                               {:cookies @cookies})]
    (if (some #(re-find #"JSESSIONID|custom.*" %) (keys (:cookies result)))
      (reset! cookies (:cookies result)))
    body))

(defn test-attr-setting
  [key value f]
  ;; set the domain & cookie name - will be active on *next* request
  ;; we need to set the cookie-name to ensure it has a known name,
  ;; regardless of test execution order
  (let [cookie-name (str "custom-" key)
        attrs (assoc {"cookie-name" cookie-name} 
                 key value)]
    (get-with-cookies (str "attrs=" (pr-str attrs)))
    (get-with-cookies "session=a:b")
    (f (@cookies cookie-name))))

(deftest setting-cookie-name
  (test-attr-setting
   "cookie-name" "custom-cookie-name"
   #(is (not (nil? %)))))

;; clj-http can't seem to see the domain, but curl confirms it is
;; getting set. Maybe the cookie the AS sends back is malformed?
#_(deftest setting-domain
    (test-attr-setting
     "domain" "biscuit"
   #(is (= "biscuit" (:domain %)))))

(deftest setting-path
  (test-attr-setting
   "path" "biscuit"
   #(is (= "biscuit" (:path %)))))

;; clj-http doesn't support the http-only attr, but curl confirms it
;; is being set
#_(deftest setting-http-only
    (test-attr-setting
     "http-only" true
     #(is (:http-only %))))

(deftest setting-secure
  (test-attr-setting
   "secure" true
   #(is (:secure %))))

(deftest setting-max-age
  (test-attr-setting
   "max-age" 5
   #(is (< (.getTime (:expires %))
           (+ (System/currentTimeMillis) 5000)))))
