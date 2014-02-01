(defproject org.immutant/immutant-namespaces-parent "1.0.3-SNAPSHOT"
  :parent [org.immutant/immutant-parent _ :relative-path "../pom.xml"]
  :plugins [[lein-modules "0.1.0-SNAPSHOT"]]
  :packaging "pom"
  :modules {:inherited {:dependencies [[org.immutant/immutant-build-support _ :scope "provided"]]}
            :versions {org.immutant/immutant-build-support    :immutant
                       org.immutant/immutant-cache-module     :immutant
                       org.immutant/immutant-daemons-module   :immutant
                       org.immutant/immutant-jobs-module      :immutant
                       org.immutant/immutant-messaging-module :immutant
                       org.immutant/immutant-web-module       :immutant
                       org.immutant/immutant-xa-module        :immutant

                       ;; This is a little weird
                       org.immutant/immutant-cache     :immutant
                       org.immutant/immutant-daemons   :immutant
                       org.immutant/immutant-jobs      :immutant
                       org.immutant/immutant-messaging :immutant
                       org.immutant/immutant-web       :immutant
                       org.immutant/immutant-xa        :immutant}})
