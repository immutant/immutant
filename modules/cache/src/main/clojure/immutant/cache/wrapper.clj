;; Copyright 2008-2013 Red Hat, Inc, and individual contributors.
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

(ns ^{:no-doc true} immutant.cache.wrapper)

(defprotocol Wrapper
  (wrap [data])
  (unwrap [data]))

;;; Default implementation doesn't wrap
(extend-type Object
  Wrapper
  (wrap [data] data)
  (unwrap [data] data))

(deftype ArrayWrapper [data]
  Object
  (equals [_ obj]
    (and (instance? ArrayWrapper obj)
         (= (seq data) (seq (.data obj)))))
  (hashCode [_]
    (.hashCode (seq data)))
  Wrapper
  (unwrap [v] (.data v)))

;;; We only wrap byte arrays, by default. Clients may extend other
;;; primitve array types, if necessary.
(extend-type (Class/forName "[B")
  Wrapper
  (wrap [data] (ArrayWrapper. data)))

