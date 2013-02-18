(ns no-clojure-jar.tests
  (:use clojure.test))

(deftest bundled-clojure-version
  (is (= "1.4.0" (clojure-version))))
