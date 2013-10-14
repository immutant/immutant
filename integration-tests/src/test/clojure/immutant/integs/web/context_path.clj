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

(ns immutant.integs.web.context-path
  (:use fntest.core
        clojure.template
        clojure.test
        [immutant.integs.integ-helper :only [base-url get-as-data get-as-data*]])
  (:require [clj-http.client :as client]))

(defn verify [path context path-info]
  (let [{:keys [result body]} (get-as-data* path)]
    (is (= "context-path" (:app body)))
    ;(println "context - path:" path "ex:" context "act:" (:context body))
    (is (= context (:context body)))
    ;(println "pathinfo - path:" path "ex:" path-info "act:" (:path-info body))
    (is (= path-info (:path-info body)))))

(deftest using-the-context-path-from-project-clj
  ((with-deployment "context_path.clj"
     {:root "target/apps/ring/context-path/"})
   (fn []
     (is (= (str (base-url) "/context-from-project") (:app-uri (get-as-data "/context-from-project"))))
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
     (is (= (str (base-url) "/context-from-descriptor") (:app-uri (get-as-data "/context-from-descriptor"))))     
     (is (= "context-path" (:app (get-as-data "/context-from-descriptor"))))
     (is (= 404
            (-> (get-as-data* "/context-from-project" {:throw-exceptions false})
                :result
                :status))))))

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
      "/%2C"                ""               "/%2C"
      "/%2F"                ""               "/%2F"
      "/%5C"                ""               "/%5C"
      "/subcontext"         "/subcontext"    "/"
      "/subcontext/"        "/subcontext"    "/"
      "/subcontext/foo"     "/subcontext"    "/foo"
      "/subcontext/foo/"    "/subcontext"    "/foo/"
      "/subcontext/%2C"     "/subcontext"    "/%2C"
      "/subcontext/%2F"     "/subcontext"    "/%2F"
      "/subcontext/%5C"     "/subcontext"    "/%5C"
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
      "/nr/%2C"                "/nr"               "/%2C"
      "/nr/%2F"                "/nr"               "/%2F"
      "/nr/%5C"                "/nr"               "/%5C"
      "/nr/foo"                "/nr"               "/foo"
      "/nr/foo/"               "/nr"               "/foo/"
      "/nr/subcontext"         "/nr/subcontext"    "/"
      "/nr/subcontext/"        "/nr/subcontext"    "/"
      "/nr/subcontext/foo"     "/nr/subcontext"    "/foo"
      "/nr/subcontext/foo/"    "/nr/subcontext"    "/foo/"
      "/nr/subcontext/%2C"     "/nr/subcontext"    "/%2C"
      "/nr/subcontext/%2F"     "/nr/subcontext"    "/%2F"
      "/nr/subcontext/%5C"     "/nr/subcontext"    "/%5C"
      "/nr/subcontext/x2"      "/nr/subcontext/x2" "/"
      "/nr/subcontext/x2/"     "/nr/subcontext/x2" "/"
      "/nr/subcontext/x2/foo"  "/nr/subcontext/x2" "/foo"
      "/nr/subcontext/x2/foo/" "/nr/subcontext/x2" "/foo/"))))

(deftest static-resource-at-root-context
  ((with-deployment "context_path.clj"
     {:root "target/apps/ring/context-path/"
      :context-path "/"})
   (fn []
     (is (= 'foo (get-as-data "/foo.txt")))
     (is (= 'foo (get-as-data "/subcontext/foo.txt")))
     (is (= 'foo (get-as-data "/subcontext/x2/foo.txt"))))))

(deftest static-resource-at-non-root-context
  ((with-deployment "context_path.clj"
     {:root "target/apps/ring/context-path/"
      :context-path "/nr"})
   (fn []
     (is (= 'foo (get-as-data "/nr/foo.txt")))
     (is (= 'foo (get-as-data "/nr/subcontext/foo.txt")))
     (is (= 'foo (get-as-data "/nr/subcontext/x2/foo.txt"))))))
