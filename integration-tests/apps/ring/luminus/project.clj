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

(defproject luminus "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [lib-noir "0.7.4"]
                 [compojure "1.1.5"]
                 [ring-server "0.3.0"]
                 [selmer "0.5.1"]
                 [com.taoensso/timbre "2.6.3"]
                 [com.postspectacular/rotor "0.1.0"]
                 [com.taoensso/tower "1.7.1"]
                 [markdown-clj "0.9.33"]]
  :aot :all
  :plugins [[lein-ring "0.8.7"]]
  :ring {:handler luminus.handler/app
         :init    luminus.handler/init
         :destroy luminus.handler/destroy}
  :profiles
  {:production {:ring {:open-browser? false
                       :stacktraces?  false
                       :auto-reload?  false}}
   :dev {:dependencies [[ring-mock "0.1.5"]
                        [ring/ring-devel "1.2.0"]]}}
  :min-lein-version "2.0.0")
