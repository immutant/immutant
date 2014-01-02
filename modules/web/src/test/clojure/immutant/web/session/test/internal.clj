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

(ns immutant.web.session.test.internal
  (:use immutant.web.session.internal
        clojure.test)
  (:require [immutant.web.test.session-utils :as util]
            [ring.middleware.cookies :as cookies]))

(defn create-response [cookies _]
  {:headers {"Set-Cookie" cookies}})

(defn mock-session-cookie-attributes []
  {:cookie-name "the-session"})

(deftest handler-should-strip-cookie-with-servlet-session-name
  (with-redefs [immutant.web.session/session-cookie-attributes mock-session-cookie-attributes]
    (are [session-id cookies expected]
         (= expected
            (util/session-fixture
             session-id
             #(get-in
               ((servlet-session-wrapper (partial create-response
                                                  (#'cookies/write-cookies cookies)))
                nil)
               [:headers "Set-Cookie"])))

         "session-id" {:the-session "session-id"}                 []
         "session-id" {:the-session "session-id", :ham "biscuit"} ["ham=biscuit"]
         "session-id" {:my-session "session-id"}                  ["my-session=session-id"]
         "session-id" {:the-session "foo"}                        ["the-session=foo"]
         "has space"  {:the-session "has space"}                  [])))
