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

(ns immutant.web.test.session
  (:use immutant.web.session
        clojure.test
        immutant.test.helpers
        immutant.web.ring
        ring.middleware.session.store))


(defn create-mock-session []
  (let [store (java.util.Hashtable.)]
    (proxy [javax.servlet.http.HttpSession]
        []
      (getAttribute [key]
        (.get store key))
      (removeAttribute [key]
        (.remove store key))
      (setAttribute [key value]
        (.put store key value))
      (invalidate []
        (.clear store)
        nil)
      (getId []
        "an-id"))))

(def ^{:dynamic true} mock-session nil)

(defn create-mock-request []
  (proxy [javax.servlet.http.HttpServletRequest]
      []
    (getSession [_]
      mock-session)))

(defn session-fixture [f]
  (binding [mock-session (create-mock-session)
            current-servlet-request (create-mock-request)]
    (f)))

(use-fixtures :each session-fixture)

(deftest read-session-should-return-a-map
  (.setAttribute mock-session
                 ":immutant.web.session/session-data"
                 {"ham" "biscuit",
                  "biscuit" :gravy})
  (let [values (read-session (servlet-store) nil)]
    (are [key exp] (= exp (values key))
         "ham" "biscuit"
         "biscuit" :gravy)))

(deftest read-empty-session-should-return-an-empty-map
  (is {} (read-session (servlet-store) nil)))

(deftest write-session-should-work
  (write-session (servlet-store) nil {"ham" "biscuit", "biscuit" :gravy})
  (let [values (read-session (servlet-store) nil)]
    (are [key exp] (= exp (values key))
         "ham" "biscuit"
         "biscuit" :gravy)))


(deftest write-session-should-return-the-session-id
  (= "an-id"
     (write-session (servlet-store) nil {"ham" "biscuit"})))

