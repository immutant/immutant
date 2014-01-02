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

(ns lobos.migrations
  ;; exclude some clojure built-in symbols so we can use the lobos' symbols
  (:refer-clojure :exclude [alter drop
                            bigint boolean char double float time])
  ;; use only defmigration macro from lobos
  (:use (lobos [migration :only [defmigration]]
          core
          schema)))

(defmigration add-authors-table
  ;; code be executed when migrating the schema "up" using "migrate"
  (up [] (create
           (table :authors (integer :id :primary-key )
             (varchar :username 100 :unique )
             (varchar :password 100)
             (varchar :email 255))))
  ;; Code to be executed when migrating schema "down" using "rollback"
  (down [] (drop (table :authors ))))

(defmigration add-posts-table
  (up [] (create
           (table :posts (integer :id :primary-key )
             (varchar :title 250)
             (text :content )
             (boolean :status (default false))
             (timestamp :created (default (now)))
             (timestamp :published )
             (integer :author [:refer :authors :id] :not-null))))
  (down [] (drop (table :posts ))))

