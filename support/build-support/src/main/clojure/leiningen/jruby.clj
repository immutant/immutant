;; lein-jruby is provided under the terms of the MIT license.

;; lein-jruby (c) 2014 Joe Kutner

;; Permission is hereby granted, free of charge, to any person
;; obtaining a copy of this software and associated documentation files
;; (the "Software"), to deal in the Software without restriction,
;; including without limitation the rights to use, copy, modify, merge,
;; publish, distribute, sublicense, and/or sell copies of the Software,
;; and to permit persons to whom the Software is furnished to do so,
;; subject to the following conditions:

;; The above copyright notice and this permission notice shall be
;; included in all copies or substantial portions of the Software.

;; THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
;; EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
;; MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
;; NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS
;; BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN
;; ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
;; CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
;; SOFTWARE.

(ns leiningen.jruby
  (:require [lancet.core :as lancet]
            [leiningen.core.classpath :as cp]
            [clojure.string :as str])
  (:import [org.apache.tools.ant.types Path]))

(defn- task-props [project]
  {:classname "org.jruby.Main"})

(.addTaskDefinition lancet/ant-project "java" org.apache.tools.ant.taskdefs.Java)

(defn jruby
  [project & keys]
  (let [url-classpath (seq (.getURLs (java.lang.ClassLoader/getSystemClassLoader)))
        classpath (str/join java.io.File/pathSeparatorChar
                    (concat (map #(.getPath %) url-classpath)
                      (map str (cp/resolve-dependencies :dependencies project))))
        task (doto (lancet/instantiate-task lancet/ant-project "java"
                     (task-props project))
               (.setClasspath (Path. lancet/ant-project classpath)))]
    (doseq [k keys] (.setValue (.createArg task) k))
    (.setFailonerror task true)
    (.setFork task true)
    (.execute task)))
