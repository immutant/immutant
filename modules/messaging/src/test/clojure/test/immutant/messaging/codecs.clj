(ns test.immutant.messaging.codecs
  (:use [immutant.messaging.codecs])
  (:use [clojure.test]))

(defn session-mock []
  (proxy [javax.jms.Session] []
    (createTextMessage [text]
      (let [properties (java.util.HashMap.)]
        (proxy [javax.jms.TextMessage] []
          (getText [] text)
          (setStringProperty [k,v]
            (.put properties k v))
          (getStringProperty [k]
            (.get properties k)))))))

(defn encoded [data encoding]
  (encode (session-mock) data {:encoding encoding}))

(deftest simple-text-with-clojure-encoding
  (let [message "message"]
    (is (= (decode (encoded message :clojure)) message))))

(deftest simple-text-with-json-encoding
  (let [message "message"]
    (is (= (decode (encoded message :json)) message))))

(deftest complex-hash-with-clojure-encoding
  (let [message {:a "b" :c [1 2 3 {:foo 42}]}]
    (is (= (decode (encoded message :clojure)) message))))

(deftest complex-hash-with-json-encoding
  (let [message {:a "b" :c [1 2 3 {:foo 42}]}]
    (is (= (decode (encoded message :json)) message))))

(deftest complex-json-encoding
  (let [message {:a "b" :c [1 2 3 {:foo 42}]}
        encoded (.getText (encoded message :json))]
    (is (= encoded "{\"a\":\"b\",\"c\":[1,2,3,{\"foo\":42}]}"))))

