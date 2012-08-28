(defproject tx "1.0.0-SNAPSHOT"
  :description "Tests in-container transactional stuff"
  :dependencies [[org.clojure/clojure "1.3.0"]
                 [korma "0.3.0-beta10"]
                 [lobos "1.0.0-SNAPSHOT"]
                 [com.h2database/h2 "1.3.160"]
                 [org.clojars.gukjoon/ojdbc "1.4"]
                 [org.clojars.kjw/mysql-connector "5.1.11"]
                 [postgresql "9.0-801.jdbc4"]
                 [net.sourceforge.jtds/jtds "1.2.4"]]
  :immutant {:swank-port 4111})