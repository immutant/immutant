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

(def ^:private logger
  (memoize
    (fn []
      (try
        (eval `(import 'org.immutant.core.Immutant))
        (eval 'Immutant/log)
        (catch Throwable _)))))

(defn print-err [args]
  (binding [*out* *err*]
    (apply println args)))

(defn debug [& args]
  (if-let [l (logger)]
    (.debug l (apply print-str args))
    ;; ignore debug messages outside the container, since that is the
    ;; tools.logging default as well
    ))

(defn info [& args]
  (if-let [l (logger)]
    (.info l (apply print-str args))
    (print-err args)))

(defn warn [& args]
  (if-let [l (logger)]
    (.warn l (apply print-str args))
    (print-err args)))

(defn error [& args]
  (if-let [l (logger)]
    (.error l (apply print-str args))
    (print-err args)))
