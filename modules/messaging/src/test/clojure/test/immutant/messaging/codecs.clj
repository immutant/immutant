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

(defn test-codec [message encoding]
  (is (= (decode (encode (session-mock) message {:encoding encoding})))))

(deftest simple-json
  (test-codec "a random text message" :json))

(deftest simple-clojure
  (test-codec "a simple text message" :clojure))

(deftest complex-json
  (test-codec {:a "b" :c [1 2 3 {:foo 42}]} :json))

(deftest complex-clojure
  (test-codec {:a "b" :c [1 2 3 {:foo 42}]} :clojure))

(deftest complex-json-encoding
  (let [message {:a "b" :c [1 2 3 {:foo 42}]}
        encoded (.getText (encode (session-mock) message {:encoding :json}))]
    (is (= encoded "{\"a\":\"b\",\"c\":[1,2,3,{\"foo\":42}]}"))))

