(ns fnbox.test.utilities
  (:use fnbox.utilities)
  (:use clojure.test)
  (:use fnbox.test.helpers))

(def a-value (ref "ham"))

(defn update-a-value []
  (dosync (ref-set a-value "biscuit")))

(defn update-a-value-with-arg [arg]
  (dosync (ref-set a-value arg)))

(deftest load-and-invoke-should-call-the-given-function
  (load-and-invoke "fnbox.test.utilities/update-a-value")
  (is (= "biscuit" @a-value)))

(deftest load-and-invoke-should-call-the-given-function-with-args
  (load-and-invoke "fnbox.test.utilities/update-a-value-with-arg" "gravy")
  (is (= "gravy" @a-value)))
