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

(ns ^{:no-doc true}
  immutant.web.internal.headers
  (:require [clojure.string :as str]
            ring.util.request))

(def charset-pattern (deref #'ring.util.request/charset-pattern))

(def default-encoding "ISO-8859-1")

(defprotocol Headers
  (get-names [x])
  (get-values [x key])
  (get-value [x key])
  (set-header [x key value])
  (add-header [x key value]))

(defn ^String get-character-encoding [headers]
  (or
    (when-let [type (get-value headers "content-type")]
      (second (re-find charset-pattern type)))
    default-encoding))

(defn headers->map [headers]
  (persistent!
    (reduce
      (fn [accum ^String name]
        (assoc! accum
          (-> name .toLowerCase)
          (->> name
            (get-values headers)
            (str/join ","))))
      (transient {})
      (get-names headers))))

(defn write-headers
  [output, headers]
  (doseq [[^String k, v] headers]
    (if (coll? v)
      (doseq [value v]
        (add-header output k (str value)))
      (set-header output k (str v)))))
