;; Copyright 2014-2015 Red Hat, Inc, and individual contributors.
;;
;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;;
;; http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.

(ns immutant.web.sse-test
  (:require [clojure.test :refer :all]
            [immutant.web.sse :refer :all]
            [immutant.web :refer (run stop)]
            [clojure.string :refer (split-lines)])
  (:import [org.glassfish.jersey.media.sse EventSource EventListener SseFeature]
           [javax.ws.rs.client ClientBuilder]))

(deftest event-formatting
  (testing "string"
    (is (= "data:foo\n"
          (event->str "foo"))))
  (testing "collection"
    (is (= "data:0\ndata:1\ndata:2\n"
          (event->str [0 1 2])
          (event->str (range 3))
          (event->str '("0" "1" "2")))))
  (testing "map"
    (let [result (event->str {:data "foo", :event "bar", :id 42, :retry 1000})
          sorted (sort (split-lines result))]
      (is (.endsWith result "\n"))
      (is (= ["data:foo" "event:bar" "id:42" "retry:1000"] sorted))))
  (testing "collection-in-map"
    (is (= "data:0\ndata:1\ndata:2\n"
          (event->str {:data (range 3)})))))

(defn event-source
  [url]
  (-> (EventSource/target 
        (-> (ClientBuilder/newBuilder)
          (.register SseFeature)
          .build
          (.target url)))
    .build))

(deftest sse
  (let [closed (promise)
        result (atom [])
        app (fn [req] 
              (as-sse-channel req
                :on-open (fn [ch]
                           (doseq [x (range 5 0 -1)]
                             (send! ch x))
                           (send! ch {:event "close", :data "bye!"})),
                :on-close (fn [ch _] (deliver closed :success))))
        server (run app)
        client (event-source "http://localhost:8080")]
    (.register client (reify EventListener
                        (onEvent [_ e]
                          (swap! result conj (.readData e))
                          (when (= "close" (.getName e))
                            (.close client)))))
    (.open client)

    ;; TODO: this...
    ;; (is (= :success (deref closed 5000 :fail)))
    ;; not this...
    (deref closed 5000 :fail)

    (is (not (.isOpen client)))
    (is (= ["5" "4" "3" "2" "1" "bye!"] @result))
    (stop server)))
