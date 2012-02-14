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

(ns immutant.web.test.ring
  (:use immutant.web.ring
        clojure.test
        immutant.test.helpers))

(deftest shuffle-sub-context-should-work
  (are [context-and-path-info input] (= context-and-path-info
                                    (apply shuffle-sub-context input))
       {:context "/" :path-info "/"}     ["/*" "/" "/"]
       {:context "/" :path-info "/ham"}  ["/*" "/" "/ham"]
       {:context  "/foo" :path-info "/"} ["/*" "/foo" "/"]
       {:context  "/bar" :path-info "/"} ["/bar/*" "/" "/bar"]
       {:context  "/foo/bar" :path-info "/"} ["/bar/*" "/foo" "/bar"]
       {:context  "/foo/bar" :path-info "/baz"} ["/bar/*" "/foo" "/bar/baz"]))

