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

(ns immutant.integs.context-path
  (:refer-clojure :exclude [get])
  (:use fntest.core
        clojure.test
        [slingshot.slingshot :only [try+]])
  (:require [clj-http.client :as client]))

(use-fixtures :once )

(defn get [path]
  (let [result (client/get (str "http://localhost:8080" path))]
    [result (read-string (:body result))]))

(deftest using-the-context-path-from-project-clj
  ((with-deployment "context_path.clj"
     {:root "target/apps/ring/context-path/"})
   (fn []
     (let [[result body] (get "/context-from-project")]
       (is (= "context-path" (:app body)))
       (is (= "/context-from-project" (:context body)))
       (is (= "/" (:path-info body))))
     (let [[result body] (get "/context-from-project/foo/bar")]
       (is (= "context-path" (:app body)))
       (is (= "/context-from-project" (:context body)))
       (is (= "/foo/bar" (:path-info body))))
     (let [[result body] (get "/context-from-project/ham")]
       (is (= "context-path" (:app body)))
       (is (= "/context-from-project/ham" (:context body)))
       (is (= nil (:path-info body)))))))

(deftest overriding-context-path-via-descriptor
  ((with-deployment "context_path.clj"
     {:root "target/apps/ring/context-path/"
      :context-path "/context-from-descriptor"})
   (fn []
     (let [[result body] (get "/context-from-descriptor")]
       (is (= "context-path" (:app body))))
     (try+
       (get "/context-from-project")
       (catch Object _
         (is (= 404 (get-in &throw-context [:object :status]))))))))

(deftest at-the-root-context
  ((with-deployment "context_path.clj"
     {:root "target/apps/ring/context-path/"
      :context-path "/"})
   (fn []
     (let [[result body] (get "")]
       (is (= "context-path" (:app body)))
       (is (= "" (:context body)))
       (is (= "/" (:path-info body))))
     (let [[result body] (get "/foo")]
       (is (= "context-path" (:app body)))
       (is (= "" (:context body)))
       (is (= "/foo" (:path-info body))))
     (let [[result body] (get "/ham")]
       (is (= "context-path" (:app body)))
       (is (= "/ham" (:context body)))
       (is (= nil (:path-info body)))))))
