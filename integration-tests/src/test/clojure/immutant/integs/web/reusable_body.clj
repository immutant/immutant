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

(ns immutant.integs.web.reusable-body
  (:use fntest.core
        clojure.test
        [immutant.integs.integ-helper :only [get-as-data]]))

(use-fixtures :once (with-deployment *file*
                      '{
                        :root "target/apps/ring/basic-ring/"
                        :init 'basic-ring.core/init-json-handler
                        :context-path "/reusable"
                        }))

;; this is really testing that the body inputstream is reusable
(deftest json-body-should-be-parsed-to-params-and-body
  (let [response (get-as-data "/reusable"
                   {:content-type :json
                    :body "{\"json\": \"data\"}"})]
    (is (= "data" (get-in response [:body "json"])))
    (is (= "data" (get-in response [:params "json"])))))

(deftest large-body-should-be-properly-read
  (let [body (apply str (repeatedly 5000 #(char (rand 127))))
        response (get-as-data "/reusable"
                   {:body body})]))
