(ns dynapath-test.in-container
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [dynapath.util :as dp]
            [immutant.util :as util]
            [immutant.registry :as registry]))

(defn classloader []
  (-> (registry/get "clojure-runtime")
      .getClassLoader))

(deftest classpath-urls-should-work
  (is (some
       #(-> %
            .toString
            (.endsWith "dynapath-0.2.3.jar"))
       (dp/classpath-urls (classloader)))))

(deftest add-classpath-url-should-work
  (is (nil? (util/try-resolve 'progress.file/done?)))
  (dp/add-classpath-url
   (classloader)
   (io/resource "progress-1.0.1.jar"))
  (is (util/try-resolve 'progress.file/done?)))
