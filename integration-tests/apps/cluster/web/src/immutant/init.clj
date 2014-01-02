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

(ns immutant.init
  (:require [immutant.web             :as web]
            [immutant.web.session     :as immutant-session]
            [ring.middleware.session  :as ring-session]
            [ring.util.response       :as ring-util]))

(defn counter [{session :session}]
  (let [count (:count session 0)
        session (assoc session :count (inc count))]
    (-> (ring-util/response (pr-str count))
        (assoc :session session))))
(web/start "/counter"
 (ring-session/wrap-session
  #'counter
  {:store (immutant-session/servlet-store)}))
