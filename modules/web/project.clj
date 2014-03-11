(defproject org.immutant/web "2.0.0-SNAPSHOT"
  :parent [org.immutant/immutant-parent _ :relative-path "../../project.clj"]
  :plugins [[lein-modules "0.2.0"]]
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.immutant/core _]
                 [ring/ring-devel _]
                 [org.projectodd.wunderboss/wunderboss-clojure _]]

  :profiles {:dev
             {:dependencies [[io.pedestal/pedestal.service "0.2.2"]
                             [clj-http "0.9.0"]]}})
