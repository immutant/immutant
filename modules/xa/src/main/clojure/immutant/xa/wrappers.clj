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

(ns immutant.xa.wrappers
  "Provide XA-compliant wrappers for java.sql interfaces"
  (:import [java.sql PreparedStatement Connection])
  (:require [immutant.xa.transaction :as tx]))

(defn prepared-statement
 [con stmt]
 (reify PreparedStatement
   ;; Make the wrapper the statement's back-reference
   (getConnection [_] con)

   ;; Delegate everything else
   (executeBatch [_] (.executeBatch stmt))
   (executeUpdate [_] (.executeUpdate stmt))
   (executeQuery [_] (.executeQuery stmt))
   (getUpdateCount [_] (.getUpdateCount stmt))
   (getParameterMetaData [_] (.getParameterMetaData stmt))
   (getGeneratedKeys [_] (.getGeneratedKeys stmt))
   (setFetchSize [_ s] (.setFetchSize stmt s))
   (setMaxRows [_ m] (.setMaxRows stmt m))
   (setNull [_ x y] (.setNull stmt x y))
   (setObject [_ x y] (.setObject stmt x y))
   (addBatch [_ b] (.addBatch stmt b))
   (addBatch [_] (.addBatch stmt))
   (close [_] (.close stmt))))

(defn connection
 [con]
 (reify Connection
   ;; Eat these since they're illegal on an XA connection
   (setAutoCommit [& _])
   (commit [_])
   (rollback [_] (tx/set-rollback-only))

   ;; Ensure statement's back-reference points to this
   (^PreparedStatement prepareStatement [this ^String a]
     (prepared-statement this (.prepareStatement con a)))
   (^PreparedStatement prepareStatement [this ^String a ^int b]
     (prepared-statement this (.prepareStatement con a b)))
   (^PreparedStatement prepareStatement [this ^String a ^int b ^int c]
     (prepared-statement this (.prepareStatement con a b c)))
   (^PreparedStatement prepareStatement [this ^String a ^int b ^int c ^int d]
     (prepared-statement this (.prepareStatement con a b c d)))

   ;; Delegate everything else
   (close [_] (.close con))
   (getAutoCommit [_] (.getAutoCommit con))
   (createStatement [_] (.createStatement con))
   (getMetaData [_] (.getMetaData con))))
