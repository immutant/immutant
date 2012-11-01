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

(ns immutant.integs.web.context-path
  (:refer-clojure :exclude [get])
  (:use fntest.core
        clojure.template
        clojure.test
        [slingshot.slingshot :only [try+]])
  (:require [clj-http.client :as client]))

(use-fixtures :once )

(defn get [path]
  (let [result (client/get (str "http://localhost:8080" path))]
    [result (read-string (:body result))]))

(defn verify [path context path-info]
  (let [[result body] (get path)]
    (is (= "context-path" (:app body)))
    ;(println "context - path:" path "ex:" context "act:" (:context body))
    (is (= context (:context body)))
    ;(println "pathinfo - path:" path "ex:" path-info "act:" (:path-info body))
    (is (= path-info (:path-info body)))))

(deftest using-the-context-path-from-project-clj
  ((with-deployment "context_path.clj"
     {:root "target/apps/ring/context-path/"})
   (fn []
     (do-template
      [path                           context                 path-info] (verify path context path-info)
      "/context-from-project"         "/context-from-project" "/"
      "/context-from-project/foo/bar" "/context-from-project" "/foo/bar"
      "/context-from-project/bar"     "/context-from-project" "/bar"))))

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
     (do-template
      [path                 context          path-info] (verify path context path-info)
      ""                    ""               "/"
      "/"                   ""               "/"
      "/foo"                ""               "/foo"
      "/foo/"               ""               "/foo/"
      "/subcontext"         "/subcontext"    "/"
      "/subcontext/"        "/subcontext"    "/"
      "/subcontext/foo"     "/subcontext"    "/foo"
      "/subcontext/foo/"    "/subcontext"    "/foo/"
      "/subcontext/x2"      "/subcontext/x2" "/"
      "/subcontext/x2/"     "/subcontext/x2" "/"
      "/subcontext/x2/foo"  "/subcontext/x2" "/foo"
      "/subcontext/x2/foo/" "/subcontext/x2" "/foo/"))))

(deftest at-a-non-root-context
  ((with-deployment "context_path.clj"
     {:root "target/apps/ring/context-path/"
      :context-path "/nr"})
   (fn []
     (do-template
      [path                    context             path-info] (verify path context path-info)
      "/nr"                    "/nr"               "/"
      "/nr/"                   "/nr"               "/"
      "/nr/foo"                "/nr"               "/foo"
      "/nr/foo/"               "/nr"               "/foo/"
      "/nr/subcontext"         "/nr/subcontext"    "/"
      "/nr/subcontext/"        "/nr/subcontext"    "/"
      "/nr/subcontext/foo"     "/nr/subcontext"    "/foo"
      "/nr/subcontext/foo/"    "/nr/subcontext"    "/foo/"
      "/nr/subcontext/x2"      "/nr/subcontext/x2" "/"
      "/nr/subcontext/x2/"     "/nr/subcontext/x2" "/"
      "/nr/subcontext/x2/foo"  "/nr/subcontext/x2" "/foo"
      "/nr/subcontext/x2/foo/" "/nr/subcontext/x2" "/foo/"))))
