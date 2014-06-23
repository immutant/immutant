;; Copyright 2014 Red Hat, Inc, and individual contributors.
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

(ns ^:no-doc immutant.messaging.codecs
  (:require [immutant.codecs :as core])
  (:import org.projectodd.wunderboss.messaging.Message))

(defn decode-with-metadata
  "Decodes the given message. If the decoded message is a clojure
   collection, the properties of the message will be affixed as
   metadata"
  [^Message msg]
  (let [result (.body msg)]
    (if (instance? clojure.lang.IObj result)
      (with-meta result (into {} (.properties msg)))
      result)))
