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

(ns ^:no-doc immutant.logging
    "Internal logging bridge. Not for public consumption.")

(defprotocol MockLogger
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
        (let [l (reify MockLogger
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
