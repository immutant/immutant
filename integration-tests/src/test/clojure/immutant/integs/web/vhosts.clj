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

(ns immutant.integs.web.vhosts
  (:use fntest.core
        clojure.test
        [immutant.integs.integ-helper :only [http-port]])
  (:require [clj-http.client :as client]))

(use-fixtures :once 
              (with-deployments {"vhost-app1"
                                 '{
                                   :root "target/apps/ring/basic-ring/"
                                   :init 'basic-ring.core/init-web
                                   :context-path "/basic-ring"
                                   :virtual-host "integ-app1.torquebox.org"
                                   }
                                 "vhost-app2"
                                 '{
                                   :root "target/apps/ring/basic-ring/"
                                   :init 'basic-ring.core/init-web
                                   :context-path "/basic-ring"
                                   :virtual-host ["integ-app2.torquebox.org"
                                                  "integ-app3.torquebox.org"]
                                   }}))

(deftest simple "it should work"
  (let [result (client/get (format "http://integ-app1.torquebox.org:%s/basic-ring" (http-port)))]
    ;;(println "RESPONSE" result)
    (is (.contains (result :body) "integ-app1.torquebox.org"))
    (is (not (.contains (result :body) "integ-app2.torquebox.org"))))

  (let [result (client/get (format "http://integ-app2.torquebox.org:%s/basic-ring" (http-port)))]
    ;;(println "RESPONSE" result)
    (is (.contains (result :body) "integ-app2.torquebox.org"))
    (is (.contains (result :body) "integ-app3.torquebox.org"))
    (is (not (.contains (result :body) "integ-app1.torquebox.org")))))




