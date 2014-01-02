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

(ns test.immutant.messaging.codecs
  (:use immutant.messaging.codecs
        clojure.test))

(defn bytes-message
  ([]
     (let [properties (java.util.Hashtable.)
           data (atom nil)]
       (proxy [javax.jms.BytesMessage] []
         (getBodyLength []
           (alength @data))
         (readBytes [bytes]
           (doseq [idx (range (alength @data))]
             (aset bytes idx (aget @data idx)))
          (alength @data))
         (writeBytes [bytes]
           (reset! data bytes))
         (setStringProperty [k,v]
           (.put properties k v))
         (getStringProperty [k]
           (.get properties k))
         (getObjectProperty [k]
            (.get properties k))
         (getPropertyNames []
            (.keys properties)))))
  ([content]
     (doto (bytes-message)
       (.writeBytes (.getBytes content)))))

(defn session-mock []
  (proxy [javax.jms.Session] []
    (createTextMessage [text]
      (let [properties (java.util.Hashtable.)]
        (proxy [javax.jms.TextMessage] []
          (getText [] text)
          (setStringProperty [k,v]
            (.put properties k v))
          (getStringProperty [k]
            (.get properties k))
          (getObjectProperty [k]
            (.get properties k))
          (getPropertyNames []
            (.keys properties)))))
    (createBytesMessage []
      (bytes-message))))

(defn test-codec [message encoding & [read-eval?]]
  (is (= message (if-not read-eval?
                   (decode-if true (encode (session-mock) message {:encoding encoding}))
                   (decode (encode (session-mock) message {:encoding encoding}))))))

(deftest json-string
  (test-codec "a random text message" :json))

(deftest clojure-string
  (test-codec "a simple text message" :clojure))

(deftest clojure-date
  (test-codec (java.util.Date.) :clojure))

(deftest clojure-date-inside-hash
  (test-codec {:date (java.util.Date.)} :clojure :read-eval))

(deftest clojure-date-inside-vector
  (test-codec [(java.util.Date.)] :clojure))

(deftest edn-string
  (test-codec "a simple text message" :edn))

(deftest edn-date
  (test-codec (java.util.Date.) :edn))

(deftest edn-date-inside-hash
  (test-codec {:date (java.util.Date.)} :edn))

(deftest edn-date-inside-vector
  (test-codec [(java.util.Date.)] :edn))

(deftest json-complex-hash
  (test-codec {:a "b" :c [1 2 3 {:foo 42}]} :json))

(deftest clojure-complex-hash
  (test-codec {:a "b" :c [1 2 3 {:foo 42}]} :clojure :read-eval))

(deftest edn-complex-hash
  (test-codec {:a "b" :c [1 2 3 {:foo 42}]} :edn))

(deftest fressian-string
  (test-codec "a simple text message" :fressian))

(deftest fressian-date
  (test-codec (java.util.Date.) :fressian))

(deftest fressian-date-inside-hash
  (test-codec {:date (java.util.Date.)} :fressian))

(deftest fressian-date-inside-vector
  (test-codec [(java.util.Date.)] :fressian))

(deftest fressian-complex-hash
  (test-codec {:a "b" :c [1 2 3 {:foo 42}]} :fressian))

(deftest complex-json-encoding
  (let [message {:a "b" :c [1 2 3 {:foo 42}]}
        encoded (encode (session-mock) message {:encoding :json})]
    (is (= message (decode encoded)))
    (is (.contains (.getText encoded) "{\"a\":\"b\",\"c\":[1,2,3,{\"foo\":42}]}"))))

(deftest text
  (test-codec "ham biscuit" :text))

(deftest text-as-bytes
  (let [text "ham biscuit"
        message (doto (bytes-message text)
                  (.setStringProperty encoding-header-name "text"))]
    (is (= text (decode message)))))

(deftest clojure-as-bytes
  (let [message (doto (bytes-message "\"ham biscuit\"")
                  (.setStringProperty encoding-header-name "clojure"))]
    (is (= "ham biscuit" (decode message)))))

(deftest edn-as-bytes
  (let [message (doto (bytes-message "\"ham biscuit\"")
                  (.setStringProperty encoding-header-name "edn"))]
    (is (= "ham biscuit" (decode message)))))

(deftest json-as-bytes
  (let [message (doto (bytes-message "[\"ham biscuit\"]")
                  (.setStringProperty encoding-header-name "json"))]
    (is (= ["ham biscuit"] (decode message)))))

(deftest default-to-text-if-no-encoding-given
  (is (= "ham biscuit" (decode (.createTextMessage (session-mock) "ham biscuit")))))
