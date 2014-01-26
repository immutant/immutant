(defproject org.immutant/immutant-jobs-module "1.0.3-SNAPSHOT"
  :parent [org.immutant/immutant-modules-parent :immutant :relative-path "../pom.xml"]
  :dependencies [[org.immutant/immutant-common-module :immutant]
                 [org.immutant/immutant-core-module :immutant]
                 [org.projectodd/polyglot-core :polyglot]
                 [org.projectodd/polyglot-jobs :polyglot]
                 [org.projectodd/polyglot-hasingleton :polyglot]
                 [org.jboss.as/jboss-as-jmx :jbossas]])

