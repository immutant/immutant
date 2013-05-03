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

(ns immutant.xa
  "Distributed XA transactional support"
  (:import javax.naming.InitialContext
           java.sql.PreparedStatement)
  (:require [immutant.registry       :as registry]
            [immutant.util           :as util]
            [immutant.xa.transaction :as tx]))

(defn datasource
  "Create an XA-capable datasource named by 'id'. The result can be
   associated with the :datasource key in a clojure.java.jdbc spec,
   e.g.

     (defonce ds (immutant.xa/datasource \"myds\" {...}))
     (clojure.java.jdbc/with-connection {:datasource ds} ...)

   The spec hash keys are adapter-specific, but all should support the
   following:
    :adapter   one of h2|oracle|mysql|postgres|mssql (required)
    :host      the host on which the database server is running [localhost]
    :port      the port on which the server is listening [adapter-specific]
    :database  the database name
    :username  the username for the database connection
    :password  the password associated with the username
    :pool      the maximum number of simultaneous connections used
    :url       a jdbc connection url (not supported by all adapters)

    :subprotocol an alias for :adapter
    :subname     an alias for :database
    :user        an alias for :username"
  [id spec]
  (let [spec (assoc spec
               :adapter  (:adapter  spec (:subprotocol spec))
               :database (:database spec (:subname spec))
               :username (:username spec (:user spec)))
        params (into {} (for [[k v] spec] [(name k) v]))
        name (.createDataSource (registry/get "xaifier") id params)
        ds (util/backoff 10 10000 (.lookup (InitialContext.) name))]
    (reify javax.sql.DataSource
      (getConnection [_]
        (let [con (.getConnection ds)]
          (reify java.sql.Connection
            (setAutoCommit [& _])
            (commit [_])
            (rollback [_])
            (close [_] (.close con))
            (getAutoCommit [_] (.getAutoCommit con))
            (createStatement [_] (.createStatement con))
            (^PreparedStatement prepareStatement [_ ^String a]
              (.prepareStatement con a))
            (^PreparedStatement prepareStatement [_ ^String a ^int b]
              (.prepareStatement con a b))
            (^PreparedStatement prepareStatement [_ ^String a ^int b ^int c]
              (.prepareStatement con a b c))
            (^PreparedStatement prepareStatement [_ ^String a ^int b ^int c ^int d]
              (.prepareStatement con a b c d))))))))

(defmacro transaction
  "Execute body within the current transaction, if available,
   otherwise invoke body within a new transaction.

  This is really just a convenient alias for
  immutant.xa.transaction/required, which is the default behavior for
  transactions in standard JEE containers. See the macros in
  immutant.xa.transaction for finer-grained transactional control
  corresponding to all the analogous attributes of the JEE @Transaction
  annotation."
  [& body]
  `(tx/required ~@body))
