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

(ns immutant.integs.nrepl
  (:use fntest.core
        clojure.test)
  (:require [clojure.tools.nrepl :as repl]))

(use-fixtures :once (with-deployment *file*
                      '{
                        :root "target/apps/ring/basic-ring/"
                        :init 'basic-ring.core/init-nrepl
                        :context-path "/basic-ring"
                        }))

(deftest simple "it should work"
  (with-open [conn (repl/connect :port 4321)]
    (let [client (repl/client conn 120000)]
      ;;(println "RESPONSE" response)
      (is (= "it works!" (first (repl/response-values
                                  (repl/message client
                                    {:op :eval :code "(str \"it works!\")"})))))
      (testing ":nrepl-middleware was successfully applied"
        (is (.contains (->> (repl/message client {:op :doc
                                                  :ns "clojure.core"
                                                  :symbol "hash-map"})
                            ;; ritz is abusing :value here :-(
                            (map :value)
                            (remove nil?)
                            first)
                       "Returns a new hash map with supplied mappings"))))))

