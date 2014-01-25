(defproject org.immutant/immutant-web-module "1.0.3-SNAPSHOT"
  :parent [org.immutant/immutant-modules-parent "1.0.3-SNAPSHOT" :relative-path "../pom.xml"]
  :dependencies [[org.immutant/immutant-core-module "1.0.3-SNAPSHOT"]
                 [org.immutant/immutant-common-module "1.0.3-SNAPSHOT"]
                 [org.projectodd/polyglot-core "1.x.incremental.61"]
                 [org.projectodd/polyglot-web "1.x.incremental.61"]
                 [org.tcrawley/dynapath "0.2.3"]
                 [ring/ring-servlet "1.2.1"]
                 [ring/ring-devel "1.2.1"]
                 [org.jboss.as/jboss-as-web "7.2.x.slim.incremental.12"]]
  :profiles {:dev {:resource-paths ["src/test/resources"]
                   :dependencies [[org.immutant/immutant-bootstrap-module "1.0.3-SNAPSHOT"]]}})

