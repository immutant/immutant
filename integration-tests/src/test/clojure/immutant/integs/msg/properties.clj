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

(ns immutant.integs.msg.properties
  (:use fntest.core)
  (:use clojure.test)
  (:use immutant.messaging))

(def ham-queue "/queue/ham")

(use-fixtures :once (with-deployment *file*
                      {
                       :root "target/apps/messaging/selector"
                       }))

(deftest properties-as-metadata
  (publish ham-queue [] :properties {
                                     :literalint     6
                                     :int            (int 6)
                                     :long           (long 6)
                                     :bigint         (bigint 6)
                                     :short          (short 6)
                                     :literalfloat   6.5
                                     :float          (float 6.5)
                                     :double         (double 6.5)
                                     :literaltrue    true
                                     :booleantrue    (boolean 6)
                                     :literalfalse   false
                                     :booleanfalse   (boolean nil)
                                     :literalstring  "{}"
                                     :hashmap        {}
                                     })
  (let [message (receive ham-queue)
        props (meta message)]
    (is (= [] message))
    (is (= 7 (inc (:literalint props))))
    (is (= 7 (inc (:int props))))
    (is (= 7 (inc (:long props))))
    (is (= 7 (inc (:bigint props))))
    (is (= 7 (inc (:short props))))
    (is (= 7.5 (inc (:literalfloat props))))
    (is (= 7.5 (inc (:float props))))
    (is (= 7.5 (inc (:double props))))
    (is (false? (not (:literaltrue props))))
    (is (false? (not (:booleantrue props))))
    (is (true? (not (:literalfalse props))))
    (is (true? (not (:booleanfalse props))))
    (is (= "{}" (:literalstring props)))
    (is (= "{}" (:hashmap props)))))

