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

(ns literals.core-test
  (:use clojure.test)
  (:require clojure.tools.reader
            literals.core
            [clj-time.core :as t]
            [immutant.messaging :as msg]))

(def publish (partial msg/publish "queue.literals"))
(def receive (partial msg/receive "queue.literals" :timeout 60000))
(def listen (partial msg/listen "queue.literals"))

(defmacro with-tools-readers [& body]
  `(binding [clojure.tools.reader/*data-readers*
             {'acme/datetime #'literals.core/from-edn-datetime}]
     (do ~@body)))

(defn test-message-encoding [enc]
  (let [t (t/now)
        r (rand)]
    ;; first for raw receive
    (println "Publishing, encoding:" enc)
    (time 
     (publish [r t] :encoding enc))
    (time
     (is (= [r t] (receive))))
    ;; then for a listener
    (let [r (inc r)
          p (promise)
          l (listen #(deliver p %))]
      (try
        (publish [r t] :encoding enc)
        (is (= [r t] (deref p 1000 :fail)))
        (finally @(msg/unlisten l))))))

;;; These will only work when data_readers.clj is auto-loaded
(when (resolve 'clojure.core/*data-readers*)
  (deftest publish-receive-edn
    (test-message-encoding :edn))
  (deftest publish-receive-clojure
    (test-message-encoding :clojure)))


;;; The rest should work with all versions

(deftest publish-receive-edn-with-tools
  (with-tools-readers (test-message-encoding :edn)))

(deftest publish-receive-clojure-with-tools
  (with-tools-readers (test-message-encoding :clojure)))

