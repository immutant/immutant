;; Copyright 2008-2014 Red Hat, Inc, and individual contributors.
;; 
;; This is free software; you can redistribute it and/or modify it
;; under the terms of the GNU Lesser General Public License as
;; published by the Free Software Foundation; either version 2.1 of
;; the License, or (at your option) any later version.
;; 
;; This software is distributed in the hope that it will be useful,
;; but WITHOUT ANY WARRANTY; without even the implied warranty of
;; MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
;; Lesser General Public License for more details.
;; 
;; You should have received a copy of the GNU Lesser General Public
;; License along with this software; if not, write to the Free
;; Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
;; 02110-1301 USA, or see the FSF site: http://www.fsf.org.

(ns immutant.repl-test
  (:use immutant.repl
        clojure.test)
  (:require [immutant.util :as u]))

(testing "init-repl"
  (deftest repl-should-start-when-there-is-no-init-in-dev-mode
    (let [passed-args (atom nil)]
      (with-redefs [start-nrepl (fn [& args] (reset! passed-args args))
                    u/dev-mode? (constantly true)]
        (init-repl nil nil)
        (is (= 0 (last @passed-args))))))
  
(deftest repl-should-not-start-when-there-is-no-init-in-!dev-mode
    (let [started? (atom nil)]
      (with-redefs [start-nrepl (fn [& _] (reset! started? true))
                    u/dev-mode? (constantly false)]
        (init-repl nil nil)
        (is (not @started?)))))

(deftest repl-should-not-start-when-port-is-explicitly-nil
  (let [started? (atom nil)]
    (with-redefs [start-nrepl (fn [& _] (reset! started? true))
                  u/dev-mode? (constantly true)]
      (init-repl {:nrepl-port nil} nil)
      (is (not @started?)))))

(deftest repl-should-use-given-port-and-interface-from-immutant-config
    (let [passed-args (atom nil)]
      (with-redefs [start-nrepl (fn [& args] (reset! passed-args args))]
        (init-repl {:nrepl-port 1 :nrepl-interface :foo} nil)
        (is (= [:foo 1] @passed-args)))))

(deftest repl-should-honor-ring-repl-opts-when-no-immutant-repl-opts-given
  (let [passed-args (atom nil)]
      (with-redefs [start-nrepl (fn [& args] (reset! passed-args args))]
        (init-repl nil {:ring {:nrepl {:port 1 :start? true}}})
        (is (= [nil 1] @passed-args)))))

(deftest repl-should-honor-ring-repl-opts-when-no-immutant-repl-opts-given-and-not-start-unless-told-to
  (let [passed-args (atom nil)]
      (with-redefs [start-nrepl (fn [& args] (reset! passed-args args))]
        (init-repl nil {:ring {:nrepl {:port 1 :start? false}}})
        (is (not @passed-args)))))

(deftest immutant-opts-should-override-ring-opts
  (let [passed-args (atom nil)]
      (with-redefs [start-nrepl (fn [& args] (reset! passed-args args))]
        (init-repl {:nrepl-port 2} {:ring {:nrepl {:port 1 :start? true}}})
        (is (= [nil 2] @passed-args)))))

(deftest repl-should-not-start-when-ring-opts-are-present-but-start?-is-false
  (let [started? (atom nil)]
    (with-redefs [start-nrepl (fn [& _] (reset! started? true))
                  u/dev-mode? (constantly true)]
      (init-repl nil {:ring {:nrepl {:port 1 :start? false}}})
      (is (not @started?)))))

(deftest repl-should-not-start-when-ring-opts-are-present-but-start?-is-missing
  (let [started? (atom nil)]
    (with-redefs [start-nrepl (fn [& _] (reset! started? true))
                  u/dev-mode? (constantly true)]
      (init-repl nil {:ring {:nrepl {:port 1}}})
      (is (not @started?)))))

(deftest repl-should-not-start-when-ring-opts-are-present-but-nrepl-port-is-nil
  (let [started? (atom nil)]
    (with-redefs [start-nrepl (fn [& _] (reset! started? true))
                  u/dev-mode? (constantly true)]
      (init-repl {:nrepl-port nil} {:ring {:nrepl {:port 1 :start? true}}})
      (is (not @started?))))))
