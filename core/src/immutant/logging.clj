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

(ns ^:no-doc immutant.logging
    "Internal logging bridge. Not for public consumption.")

(defprotocol Logger
  (trace [this msg])
  (debug [this msg])
  (info [this msg])
  (warn [this msg])
  (error [this msg]))

(defn print-err [level msg]
  (binding [*out* *err*]
    (println level msg)))

(def ^:private logger-lookup-fn
  (delay
    (try
      (eval `(import 'org.immutant.core.Immutant))
      (eval '(fn [n] (Immutant/getLogger n)))
      (catch Throwable _
        (let [l (reify Logger
                  (trace [_ msg])
                  (debug [_ msg])
                  (info [_ msg]
                    (print-err "INFO:" msg))
                  (warn [_ msg]
                    (print-err "WARN:" msg))
                  (error [_ msg]
                    (print-err "ERROR:" msg)))]
          (constantly l))))))

(defn ^:private logger [name]
  (@logger-lookup-fn name))

(defn log* [ns level msg]
  (let [l (logger ns)]
    (case level
      :trace (.trace l msg)
      :debug (.debug l msg)
      :info  (.info l msg)
      :warn  (.warn l msg)
      :error (.error l msg))))

(defmacro log [level args]
  (let [ns# (-> *ns* ns-name name)]
    `(log* ~ns# ~level (print-str ~@args))))

(defmacro trace [& args]
  `(log :trace ~args))

(defmacro debug [& args]
  `(log :debug ~args))

(defmacro info [& args]
  `(log :info ~args))

(defmacro warn [& args]
  `(log :warn ~args))

(defmacro error [& args]
  `(log :error ~args))
