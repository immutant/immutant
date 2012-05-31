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

(ns immutant.web.test.session-handler
  (:use immutant.web.session.handler
        clojure.test)
  (:require [immutant.web.test.session-utils :as util]
            [ring.middleware.cookies :as cookies]))

(defn create-response [cookies _]
  {:headers {"Set-Cookie" cookies}})

(deftest handler-should-strip-cookie-with-session-id
  (are [session-id cookies expected]
       (= expected
          (util/session-fixture
           session-id
           #(get-in
             ((remove-ring-session-cookie (partial create-response
                                                   (#'cookies/write-cookies cookies)))
              nil)
             [:headers "Set-Cookie"])))

       "foo"        {:ring-session "foo"}                 []
       "foo"        {:ring-session "foo", :ham "biscuit"} ["ham=biscuit"]
       "foo"        {:my-session "foo"}                   []
       "has spaces" {:ring-session "has spaces"}          []))
