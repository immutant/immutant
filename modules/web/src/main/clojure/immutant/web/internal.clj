;; Copyright 2008-2012 Red Hat, Inc, and individual contributors.
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

(ns immutant.web.internal
  (:require
   [immutant.registry  :as reg]
   [immutant.utilities :as util])
  (use [immutant.try :only [try-defn try-def]]))

(def ^{:dynamic true} ^javax.servlet.http.HttpServletRequest current-servlet-request nil)

(def ^{:private true} servlet-info (atom {}))

(defn get-servlet-info [name]
  (@servlet-info name))

(defn store-servlet-info! [name info]
  (swap! servlet-info assoc name info))

(defn remove-servlet-info! [name]
  (when-let [info (@servlet-info name)]
    (swap! servlet-info dissoc name)
    info))

(defn servlet-name [path]
  (str "immutant.ring." (util/app-name) "." path))

(defn normalize-subcontext-path 
  "normalize subcontext path so it matches Servlet Spec v2.3, section 11.2"
  [path]
  (loop [p path]
    (condp re-matches p
      #"^(/[^*]+)*/\*$" p                     ;; /foo/* || /*
      #"^(|[^/].*)"     (recur (str "/" p))   ;; prefix with /
      #".*?[^/]$"       (recur (str p "/"))   ;; add final /
      #"^(/[^*]+)*/$"   (recur (str p "*"))   ;; postfix with *
      (throw (IllegalArgumentException.
              (str "The context path \"" path "\" is invalid. It should be \"/\", \"/foo\", \"/foo/\", \"foo/\", or \"foo\""))))))

;; We have a race condition at deployment - the context isn't init'ed enough for
;; us to install servlets from clojure, and won't be until after the deployment
;; finishes. So we do context operations in a future that waits for the context
;; to become ready.
(defmacro when-context-initialized [context & body]
  `(future
     (loop [count# 10000]
       (if (.isInitialized ~context)
         (do
           ~@body)
         (do
           (Thread/sleep 1)
           (if (= 0 count#)
             (throw (RuntimeException. "Giving up waiting for web-context to initialize"))
             (recur (dec count#))))))))

(defn virtual-hosts []
  (let [vh (or (:virtual-host (reg/fetch :config)) ["default-host"])]
    (if (coll? vh)
      vh
      [vh])))

(def reqs '(import '(org.apache.catalina.core StandardContext)))

(try-defn reqs install-servlet [servlet-class sub-context-path]
  (let [context (reg/fetch "web-context")
        name (servlet-name sub-context-path)
        wrapper (.createWrapper context)
        mapper (-> (reg/fetch "jboss.web")
                   (.getService)
                   (.getMapper))]
    (when-context-initialized
     context
     (doto wrapper
       (.setName name)
       (.setServletClass servlet-class)
       (.setEnabled true)
       (.setDynamic true)
       (.setLoadOnStartup -1))
     (doto context
       (.addChild wrapper)
       (.addServletMapping sub-context-path name))
     (doseq [host (virtual-hosts)]
       (.addWrapper mapper host (.getPath context) sub-context-path wrapper)))
    wrapper))

(try-defn reqs remove-servlet [sub-context-path wrapper]
  (let [context (reg/fetch "web-context")
        mapper (-> (reg/fetch "jboss.web")
                   (.getService)
                   (.getMapper))]
    (when-context-initialized
     context
     (.removeServletMapping context sub-context-path)
     (if wrapper (.removeChild context wrapper))
     (doseq [host (virtual-hosts)]
       (try
         (.removeWrapper mapper host (.getPath context) sub-context-path)
         (catch Exception _)))))
  nil)



