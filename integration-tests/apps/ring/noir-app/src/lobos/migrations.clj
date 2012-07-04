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
             (varchar :password 100 :not-null )
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

