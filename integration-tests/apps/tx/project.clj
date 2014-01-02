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

(defproject tx "1.0.0-SNAPSHOT"
  :description "Tests in-container transactional stuff"
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [korma "0.3.0-RC2"]
                 [lobos "1.0.0-SNAPSHOT"]
                 [com.h2database/h2 "1.3.160"]
                 [org.clojars.gukjoon/ojdbc "1.4"]
                 [mysql/mysql-connector-java "5.1.22"]
                 [postgresql "9.1-901.jdbc4"]
                 [net.sourceforge.jtds/jtds "1.2.4"]]
  :immutant {:swank-port 4005})
