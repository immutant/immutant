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

(defmacro test-for [name message encoding]
  (list 'deftest name
        `(let [~'message ~message
               ~'encoded (encode (session-mock) ~'message {:encoding ~encoding})]
           (is (= (decode ~'encoded) ~'message)))))

(test-for simple-json     "a random text message"       :json)
(test-for simple-clojure  "a simple text message"       :clojure)
(test-for complex-json    {:a "b" :c [1 2 3 {:foo 42}]} :json)
(test-for complex-clojure {:a "b" :c [1 2 3 {:foo 42}]} :clojure)

(deftest complex-json-encoding
  (let [message {:a "b" :c [1 2 3 {:foo 42}]}
        encoded (.getText (encode (session-mock) message {:encoding :json}))]
    (is (= encoded "{\"a\":\"b\",\"c\":[1,2,3,{\"foo\":42}]}"))))

