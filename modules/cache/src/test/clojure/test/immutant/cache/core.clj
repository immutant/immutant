(ns test.immutant.cache.core
  (:use immutant.cache.core
        clojure.test))

(deftest test-different-persist-config
  (let [c1 (build-config {:persist "src"})
        c2 (build-config {:persist "target"})]
    (is (not (same-config? c1 c2)))))

(deftest test-different-locking-config
  (let [c1 (build-config {:locking :optimistic})
        c2 (build-config {:locking :pessimistic})]
    (is (not (same-config? c1 c2)))))