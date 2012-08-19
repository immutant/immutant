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

(ns immutant.xa
  "Distributed XA transactional support"
  (:import javax.naming.InitialContext)
  (:require [immutant.registry :as lookup]
            [immutant.utilities :as util]
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
    :pool      the maximum number of simultaneous connections used"
  [id spec]
  (let [params (into {} (for [[k v] spec] [(name k) v]))
        name (.createDataSource ^org.immutant.xa.XAifier (lookup/fetch "xaifier") id params)]
    (util/backoff 10 10000 (.lookup ^javax.naming.InitialContext (InitialContext.) name))))

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

