(ns tx.test.scope
  (:use clojure.test)
  (:require [immutant.xa.transaction :as tx]
            [immutant.cache :as c]))

;;; Create a cache for testing transactional scope behavior
(def cache (c/cache "test"))

;;; Clear cache before each test
(use-fixtures :each (fn [f] (c/delete-all cache) (f)))

(defmacro catch-exception [& body]
  `(try ~@body (catch Exception _#)))

(deftest required-commit
  (tx/required
   (c/put cache :a 1)
   (tx/required
    (c/put cache :b 2)))
  (is (= 1 (:a cache)))
  (is (= 2 (:b cache))))

(deftest required-rollback-parent
  (catch-exception
   (tx/required
    (c/put cache :a 1)
    (tx/required
     (c/put cache :b 2))
    (throw (Exception.))))
  (is (empty? cache)))

(deftest required-rollback-parent-sro
  (tx/required
   (c/put cache :a 1)
   (tx/required
    (c/put cache :b 2))
   (tx/set-rollback-only))
  (is (empty? cache)))

(deftest required-rollback-child
  (catch-exception
   (tx/required
    (c/put cache :a 1)
    (tx/required
     (c/put cache :b 2)
     (throw (Exception.)))))
  (is (empty? cache)))

(deftest required-rollback-child-sro
  (tx/required
   (c/put cache :a 1)
   (tx/required
    (c/put cache :b 2)
    (tx/set-rollback-only)))
  (is (empty? cache)))

(deftest requires-new-commit
  (tx/required
   (c/put cache :a 1)
   (tx/requires-new
    (c/put cache :b 2)))
  (is (= 1 (:a cache)))
  (is (= 2 (:b cache))))

(deftest requires-new-rollback-parent
  (catch-exception
   (tx/required
    (c/put cache :a 1)
    (tx/requires-new
     (c/put cache :b 2))
    (throw (Exception.))))
  (is (nil? (:a cache)))
  (is (= 2 (:b cache))))

(deftest requires-new-rollback-parent-sro
  (tx/required
   (c/put cache :a 1)
   (tx/requires-new
    (c/put cache :b 2))
   (tx/set-rollback-only))
  (is (nil? (:a cache)))
  (is (= 2 (:b cache))))

(deftest requires-new-rollback-child
  (tx/required
   (c/put cache :a 1)
   (catch-exception
    (tx/requires-new
     (c/put cache :b 2)
     (throw (Exception.)))))
  (is (= 1 (:a cache)))
  (is (nil? (:b cache))))

(deftest requires-new-rollback-child-sro
  (tx/required
   (c/put cache :a 1)
   (tx/requires-new
    (c/put cache :b 2)
    (tx/set-rollback-only)))
  (is (= 1 (:a cache)))
  (is (nil? (:b cache))))

(deftest mandatory-commit
  (tx/required
   (c/put cache :a 1)
   (tx/mandatory
    (c/put cache :b 2)))
  (is (= 1 (:a cache)))
  (is (= 2 (:b cache))))

(deftest mandatory-rollback
  (is (thrown? Exception
               (c/put cache :a 1)
               (tx/mandatory
                (c/put cache :b 2))))
  (is (= 1 (:a cache)))
  (is (nil? (:b cache))))

(deftest never-saves
  (c/put cache :a 1)
  (tx/never
   (c/put cache :b 2))
  (is (= 1 (:a cache)))
  (is (= 2 (:b cache))))

(deftest never-rollback
  (is (thrown? Exception
               (tx/required
                (c/put cache :a 1)
                (tx/never
                 (c/put cache :b 2)))))
  (is (empty? cache)))

(deftest not-supported-rollback-parent
  (catch-exception
   (tx/required
    (c/put cache :a 1)
    (tx/not-supported
     (c/put cache :b 2))
    (throw (Exception.))))
  (is (nil? (:a cache)))
  (is (= 2 (:b cache))))

(deftest not-supported-rollback-parent-sro
  (tx/required
   (c/put cache :a 1)
   (tx/not-supported
    (c/put cache :b 2))
   (tx/set-rollback-only))
  (is (nil? (:a cache)))
  (is (= 2 (:b cache))))

(deftest not-supported-rollback-child
  (tx/required
   (c/put cache :a 1)
   (catch-exception
    (tx/not-supported
     (c/put cache :b 2)
     (throw (Exception.)))))
  (is (= 1 (:a cache)))
  (is (= 2 (:b cache))))

(deftest supports-saves
  (catch-exception
   (tx/supports
    (c/put cache :a 1)
    (throw (Exception.))))
  (is (= 1 (:a cache))))

(deftest supports-rollback-parent
  (catch-exception
   (tx/required
    (c/put cache :a 1)
    (tx/supports
     (c/put cache :b 2))
    (throw (Exception.))))
  (is (empty? cache)))

(deftest supports-rollback-parent-sro
  (tx/required
   (c/put cache :a 1)
   (tx/supports
    (c/put cache :b 2))
   (tx/set-rollback-only))
  (is (empty? cache)))

(deftest supports-rollback-child
  (catch-exception
   (tx/required
    (c/put cache :a 1)
    (tx/supports
     (c/put cache :b 2)
     (throw (Exception.)))))
  (is (empty? cache)))

(deftest supports-rollback-child-sro
  (tx/required
   (c/put cache :a 1)
   (tx/supports
    (c/put cache :b 2)
    (tx/set-rollback-only)))
  (is (empty? cache)))

