(ns noir-app.models
  (:use korma.db
        korma.core))
 
(defdb memdb {:classname "org.h2.Driver"
              :subprotocol "h2"
              :subname "mem:foo"})

(defentity authors)
(defentity posts)

(insert authors (values {:id 1, :username "jim", :password "password", :email "jim AT redhat.com"}) )
