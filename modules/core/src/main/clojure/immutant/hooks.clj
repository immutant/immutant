;; Copyright 2008-2012 Red Hat, Inc, and individual contributors.
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
(ns immutant.hooks
  "Monkeypatching."
  (:require [robert.hooke :as bob]))

(comment 
  (defmacro time-it*
    [tag expr]
    `(let [start# (. System (nanoTime))
           ret# ~expr]
       (println (str ~tag " took " (/ (double (- (. System (nanoTime)) start#)) 1000000.0) "ms"))
       ret#))


  (defn time-it [name f & args]
    (time-it*
     (str name " " args)
     (apply f args)))

  (def time-require (partial time-it "require"))
  (def time-use (partial time-it "use"))
  (def time-import (partial time-it "import"))

  (bob/add-hook #'clojure.core/require #'time-require)
  (bob/add-hook #'clojure.core/use #'time-use)
  (bob/add-hook #'clojure.core/import #'time-import))
