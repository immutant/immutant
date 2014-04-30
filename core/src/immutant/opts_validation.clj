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

(ns ^:no-doc ^:internal immutant.opts-validation
  "Functions for validating options."
  (:require [clojure.walk  :refer [stringify-keys]])
  (:import [org.projectodd.wunderboss Option]))

(defmacro set-valid-options! [v opts]
  (let [v# (resolve v)]
    `(alter-meta! ~v# assoc :valid-options ~opts)))

(defn valid-options-for [src]
  (-> src meta :valid-options))

(defn validate-options*
  [name valid-keys opts]
  (if (::validated? opts)
    opts
    (do
      (doseq [k (keys opts)]
        (if-not (valid-keys k)
          (throw (IllegalArgumentException.
                   (format "%s is not a valid option for %s, valid options are: %s"
                     k name valid-keys)))))
      (assoc opts ::validated? true))))

(defmacro validate-options
  "Validates that (keys opts) is a subset of :valid-options from (meta src)"
  ([src opts]
     `(validate-options ~src ~src ~opts))
  ([alt-name src opts]
     (let [src-var# (resolve src)]
       `(validate-options* (name (quote ~alt-name))
          (valid-options-for ~src-var#) ~opts))))

(defmacro concat-valid-options
  "Grabs the :valid-options metadata from all of the passed vars, and concats them together into a set."
  [& vars]
  (let [vars# (mapv resolve vars)]
    `(set (mapcat valid-options-for ~vars#))))

(defn opts->map
  "Converts an Option class into a map of name -> Option instance."
  [class]
  (->> class
    Option/optsFor
    (map #(vector (.name %) %))
    (into {})))

(defn opts->keywords
  "Converts an Option class into a list of names for those Options as keywords.
   Auto-converts \"foo_bar\" to :foo-bar."
  [class]
  (->> class opts->map keys (map #(keyword (.replace % \_ \-)))))

(defn opts->set
  "Converts an Option class into a set of keywords."
  [class]
  (-> class opts->keywords set))

(defn extract-options
  "Converts a clojure map into a WunderBoss options map."
  [m c]
  (let [optsm (opts->map c)]
    (->> m
      stringify-keys
      (map (fn [[k v]]
             (when-let [opts (optsm k (optsm (.replace k \- \_)))]
               [opts v])))
      (into {}))))
