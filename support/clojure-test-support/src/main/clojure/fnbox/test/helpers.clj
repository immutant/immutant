(ns fnbox.test.helpers
  (:use clojure.test)
  (:require [clojure.template :as temp]))

(defmacro is-not
  ;; NOTE: this won't work with any assert-expr's
  ([form] `(is-not ~form nil))
  ([form msg] `(try-expr ~msg (not ~form))))

(defmacro are-not
  [argv expr & args]
  `(temp/do-template ~argv (is-not ~expr) ~@args))

(defmethod assert-expr 'not-thrown? [msg form]
  ;; (is (not-thrown? c expr))
  ;; Asserts that evaluating expr does not throw an exception of class c.
  (let [klass (second form)
        body (nthnext form 2)]
    `(try ~@body
          (do-report {:type :pass, :message ~msg,
                   :expected '~form, :actual '~form})
          (catch ~klass e#
            (do-report {:type :fail, :message ~msg,
                     :expected '~form, :actual e#})
            e#))))

(defmacro deftest-pending
  [name & body]
   ;; borrowed from http://techbehindtech.com/2010/06/01/marking-tests-as-pending-in-clojure/                  
   (let [message (str "\n========\nPENDING: " name "\n========\n")]
     `(deftest ~name
        (println ~message))))
