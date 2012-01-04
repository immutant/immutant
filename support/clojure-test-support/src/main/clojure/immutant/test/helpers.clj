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

(ns immutant.test.helpers
  (:use clojure.test)
  (:require [clojure.template :as temp]))

(defmacro is-not
  ;; NOTE: this won't work with any assert-expr's
  ([form] `(is-not ~form nil))
  ([form msg] `(try-expr ~msg (not ~form))))

(defmacro are-not
  [argv expr & args]
  `(temp/do-template ~argv (is-not ~expr) ~@args))

(defmethod assert-expr 'not-thrown? [msg form]
  ;; (is (not-thrown? c expr))
  ;; Asserts that evaluating expr does not throw an exception of class c.
  (let [klass# (second form)
        body# (nthnext form 2)]
    `(try ~@body#
          (do-report {:type :pass, :message ~msg,
                   :expected '~form, :actual '~form})
          (catch ~klass# e#
            (do-report {:type :fail, :message ~msg,
                     :expected '~form, :actual e#})
            e#))))

(defmacro deftest-pending
  [name & body]
   ;; borrowed from http://techbehindtech.com/2010/06/01/marking-tests-as-pending-in-clojure/                  
   (let [message# (str "\n========\nPENDING: " name "\n========\n")]
     `(deftest ~name
        (println ~message#))))
