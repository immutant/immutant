;; Copyright 2008-2011 Red Hat, Inc, and individual contributors.
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

(ns immutant.web.test.core
  (:use immutant.web.core)
  (:use clojure.test)
  (:use immutant.test.helpers))


(deftest normalize-subcontext-path-should-work
  (are [exp input] (= exp (normalize-subcontext-path input))
       "/*" "/"
       "/*" ""
       "/*" "/*"
       "/foo/*" "/foo"
       "/foo/*" "/foo/"
       "/foo/*" "foo/"
       "/foo/*" "foo"
       "/foo/*" "/foo/*"
       "/foo/*" "foo/*"
       "/foo/bar/*" "/foo/bar"
       "/foo/bar/*" "/foo/bar/"
       "/foo/bar/*" "foo/bar/"
       "/foo/bar/*" "foo/bar"
       "/foo/bar/*" "/foo/bar/*"
       "/foo/bar/*" "foo/bar/*"))

(deftest normalize-subcontext-path-should-raise-when-invalid
  (are [input]
       (thrown-with-msg?
         IllegalArgumentException
         #"invalid"
         (normalize-subcontext-path input))
       "//"
       "/*/"
       "asdf*asdf"))
