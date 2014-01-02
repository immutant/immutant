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

(ns tx.korma
  (:require [lobos.config :as db]
            [immutant.xa  :as xa])
  (:use korma.db
        korma.core))

(def spec {:subprotocol "h2" :datasource (xa/datasource "lobos" {:adapter "h2" :database "mem:lobos"})})

(defn init []
  (db/create spec)
  (defdb prod spec)

  (defentity authors)
  (defentity posts))

