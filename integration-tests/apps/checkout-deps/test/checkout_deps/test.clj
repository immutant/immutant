(ns checkout-deps.test
  (:use clojure.test)
  (:require [clojure.java.io :as io]
            [checkout-test-project.core :as ctp]))

(deftest checkout-version-should-be-loaded
  (is (= :checkout ctp/lib-source)))

(deftest resources-should-be-loaded-from-the-checkout-version
  (is (= "checkout" (-> "checkout-test-resource.txt"
                        io/resource
                        slurp
                        clojure.string/trim))))
