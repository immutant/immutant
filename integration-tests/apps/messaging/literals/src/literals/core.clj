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

(ns literals.core
  (:require [clj-time.format :as tf])
  (:import [org.joda.time DateTime Interval]))

;; ## For Clojure Reader

(def ^:private reader-datetime-formatter (tf/formatters :date-hour-minute-second-ms))

(defn from-edn-datetime
  [dt]
  (tf/parse reader-datetime-formatter dt))

(defmethod print-method DateTime [o ^java.io.Writer w]
  (let [prefix "#acme/datetime"
        dt     (tf/unparse reader-datetime-formatter o)]
    (.write w (str prefix " \"" dt "\""))))
