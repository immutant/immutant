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

(deftest test-different-eviction-config
  (let [c1 (build-config {:max-entries 100})
        c2 (build-config {:max-entries 1000})]
    (is (not (same-config? c1 c2)))))

(deftest test-default-eviction-config
  (let [c1 (build-config {:max-entries 100})
        c2 (build-config {:max-entries 100 :eviction :lirs})]
    (is (same-config? c1 c2))))

